package capcollator

import grails.transaction.Transactional
import groovy.xml.MarkupBuilder
import java.io.File
import java.io.FileWriter
import groovy.xml.XmlUtil
import java.text.SimpleDateFormat
import java.util.Calendar
import groovy.util.XmlParser


@Transactional
class StaticFeedService {

  def grailsApplication
  def alertCacheService
  public static int MAX_FEED_ENTRIES = 100;


  def update(routingKey, body, context) {
    String[] key_components = routingKey.split('\\.');
    if ( key_components.length == 2 ) {
      String sub_name = key_components[1]
      String full_path = grailsApplication.config.staticFeedsDir+'/'+sub_name;
      File sub_dir = new File(full_path)
      if ( ! sub_dir.exists() ) {
        log.debug("Setting up new static sub DIR ${full_path}");
        sub_dir.mkdirs()
      }
      else {
        log.debug("${full_path} already present");
      }

      File rss_file = new File(full_path+'/rss.xml')
      if ( ! rss_file.exists() ) {
        log.debug("Create starter feed - ${full_path}/rss.xml");
        createStarterFeed(full_path, sub_name);
      }
      else {
        log.debug("${full_path}/rss.xml present");
      }

      addItem(full_path, body, sub_name)
    }
    else {
      log.error("Unexpected number of routing key components:: ${key_components}");
    }
  }

  private void createStarterFeed(String path, subname) {

    // <?xml version='1.0' encoding='UTF-8'?>
    // <rss xmlns:atom="http://www.w3.org/2005/Atom" version="2.0">
    //   <channel>
    //     <atom:link rel="self" href="https://s3-eu-west-1.amazonaws.com/alert-feeds/unfiltered/rss.xml" type="application/rss+xml"/>
    //     <atom:link rel="alternate" title="RSS" href="https://s3-eu-west-1.amazonaws.com/alert-feeds/unfiltered/rss.xml" type="application/rss+xml"/>
    //     <title>Latest Valid CAP alerts received, unfiltered</title>
    //     <link>https://s3-eu-west-1.amazonaws.com/alert-feeds/unfiltered/rss.xml</link>
    //     <description>This feed lists the most recent valid CAP alerts uploaded to the Filtered Alert Hub.</description>
    //     <language>en</language>
    //     <copyright>public domain</copyright>
    //     <pubDate>Sun, 26 Aug 2018 21:23:20 GMT</pubDate>
    //     <lastBuildDate>Wed, 4 Jan 2017 11:31:34 GMT</lastBuildDate>
    //     <docs>http://blogs.law.harvard.edu/tech/rss</docs>
    //     <image>
    //      <title>Latest Valid CAP alerts received, unfiltered</title>
    //      <url>https://s3-eu-west-1.amazonaws.com/alert-feeds/unfiltered/capLogo.jpg</url>
    //      <link>https://s3-eu-west-1.amazonaws.com/alert-feeds/unfiltered/rss.xml</link>
    //     </image>
    //   </channel>
    // </rss>

    def fileWriter = new FileWriter(path+'/rss.xml');
    def rssBuilder = new MarkupBuilder(fileWriter)

    def sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z")
    def pub_date_str = sdf.format(new Date());

    rssBuilder.'rss'(// 'xmlns':'http://www.w3.org/2005/Atom', -- The namespace for the document is not ATON
                     'xmlns:rss':'http://www.rssboard.org/rss-specification',
                     'xmlns:atom':'http://www.w3.org/2005/Atom',
                     'xmlns:dc':'http://purl.org/dc/elements/1.1/', 
                     version:"2.0") {
      channel {
        'atom:link'(rel:'self',href:"${grailsApplication.config.staticFeedsBaseUrl}/${subname}/rss.xml", type:"application/rss+xml")
        'atom:link'(rel:'alternate',title:'RSS',href:"${grailsApplication.config.staticFeedsBaseUrl}/${subname}/rss.xml", type:"application/rss+xml")
        title("Latest Valid CAP alerts received, ${subname}")
        link("${grailsApplication.config.staticFeedsBaseUrl}/${subname}")
        description("This feed lists the most recent valid CAP alerts uploaded to the Filtered Alert Hub.")
        language("en");
        copyright("public domain");
        pubDate(pub_date_str);
        lastBuildDate(pub_date_str);
        docs("http://blogs.law.harvard.edu/tech/rss");
        image {
          title("Latest Valid CAP alerts received, ${subname}");
          url("${grailsApplication.config.staticFeedsBaseUrl}/${subname}/capLogo.jpg");
          link("${grailsApplication.config.staticFeedsBaseUrl}/${subname}/rss.xml");
        }
      }
    }
    fileWriter.close();
  }


  private void addItem(String path, node, subname) {

    // log.debug("addItem(${path},${node})");
    if ( node?.AlertMetadata?.capCollatorUUID ) {
      log.debug("capCollatorUUID: ${node.AlertMetadata.capCollatorUUID}")
      def source_alert = alertCacheService.get(node.AlertMetadata.capCollatorUUID);

      String static_alert_file = writeAlertFile(path, node, source_alert);
  
      log.debug("Parse existing RSS at ${path}/rss.xml");
      // def xml = new XmlSlurper().parse(path+'/rss.xml')
      def xml = new XmlParser().parse(new File(path+'/rss.xml'))
  
      //Edit File e.g. append an element called foo with attribute bar
  
      log.debug("Get first info section");
      def info = getFirstInfoSection(node);

      def formatted_pub_date = null;
      try {
        def sdf = new SimpleDateFormat('yyyy-MM-dd\'T\'HH:mm:ssX')
        def alert_date = sdf.parse(node?.AlertBody?.sent);
        formatted_pub_date = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z").format(alert_date);
      }
      catch ( Exception e ) {
      }

      def new_item_node = xml.channel[0].appendNode( 'item' );
      new_item_node.appendNode( 'title', info?.headline ?: info?.description );
      new_item_node.appendNode( 'link', "${grailsApplication.config.staticFeedsBaseUrl}/${subname}${static_alert_file}".toString());
      new_item_node.appendNode( 'description', info?.description);
      new_item_node.appendNode( 'pubDate', formatted_pub_date ?: node?.AlertBody?.sent);
      new_item_node.appendNode( 'guid', node.AlertMetadata.capCollatorUUID);
      //      //'dc:creator'('creator')
      //      //'dc:date'('date')

      // xml.channel[0].value = xml.channel[0].children().sort { a,b ->

      // The true asks the sort to mutate the source list. Source elements without a pubDate element high - so the none item
      // entries float to the top of the list
      xml.channel[0].children().sort(true) { a,b ->
        ( b.'link'?.text() ?: 'zzz'+(b.name().toString() ) ).compareTo( ( a.'link'?.text() ?: 'zzz'+(a.name().toString() ) ) )
      }

      log.debug("Trim rss feed. Size before: ${xml.channel[0].children().size()}");
      int ctr = MAX_FEED_ENTRIES;
      xml.channel[0] = xml.channel[0].item.each { n ->
        if ( ctr > 0 ) {
          ctr--;
        }
        else {
          log.debug("remove...");
          n.replaceNode{}
        }
      }
      log.debug("Trim rss feed. Size after: ${xml.channel[0].children().size()}");

      // Limit to 100 items
      // def origianl_list = xml.channel[0].item
      // xml.channel.item = xml.channel[0].item.take(100)

      // def removed_elements = origianl_list.removeAll(xml.channel.item)
      // removed_elements.each { re ->
      //   log.debug("Remove: ${re?.link}");
      // }
  
      //Save File
      def writer = new FileWriter(path+'/rss.xml')
  
      // Append new element
  
      // then sort in date order desc
      // rootNode.children().sort(true) {it.attribute('name')}
  
      //Option 1: Write XML all on one line
      // def builder = new StreamingMarkupBuilder()
      // writer << builder.bind {
      //   mkp.yield xml
      // }
  
      //Option 2: Pretty print XML
      XmlUtil.serialize(xml, writer)
      writer.flush()
      writer.close()
    }
  }

  private Map getFirstInfoSection(node) {
    Map result = null;
    if ( node.AlertBody.info instanceof List ) 
      result = node.AlertBody.info.get(0);
    else
      result = node.AlertBody.info

    return result
  }

  private String writeAlertFile(path, node, content) {
    // https://alert-hub.s3.amazonaws.com/us-epa-aq-en/2018/09/07/12/28/2018-09-07-12-28-41-693.xml
    // log.debug("writeAlertNode ${new String(content)}");
    def sdf = new SimpleDateFormat('yyyy-MM-dd\'T\'HH:mm:ssX')
    def alert_date = sdf.parse(node?.AlertBody?.sent);
    def cal = Calendar.getInstance()
    cal.setTime(alert_date);
    // def alert_path = "${cal.get(Calendar.YEAR)}/${cal.get(Calendar.MONTH)}/${cal.get(Calendar.DAY_OF_MONTH)}/${cal.get(Calendar.HOUR_OF_DAY)}}/${cal.get(Calendar.MINUTE)}"
    def alert_path = sprintf('/%02d/%02d/%02d/%02d/%02d/',[cal.get(Calendar.YEAR),cal.get(Calendar.MONTH)+1,cal.get(Calendar.DAY_OF_MONTH),cal.get(Calendar.HOUR_OF_DAY),cal.get(Calendar.MINUTE)]);
    log.debug("Write to ${path}${alert_path}")

    File alert_path_dir = new File(path+alert_path)
    if ( ! alert_path_dir.exists() ) {
      log.debug("Setting up new static sub DIR ${alert_path_dir}");
      alert_path_dir.mkdirs()
    }

    String full_alert_path = alert_path+((node?.AlertBody?.sent).replaceAll(':','-'))+'.xml'
    File new_alert_file = new File(path+full_alert_path)

    new_alert_file << content

    return full_alert_path
  }
}
