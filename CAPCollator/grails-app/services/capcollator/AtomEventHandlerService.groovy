package capcollator

import grails.transaction.Transactional
import com.budjb.rabbitmq.publisher.RabbitMessagePublisher

@Transactional
class AtomEventHandlerService {

  RabbitMessagePublisher rabbitMessagePublisher

  def process(cap_url) {
    log.debug("AtomEventHandlerService::process ${cap_url}");
  }


  def handleNotification(body,context) {
    log.debug("AtomEventHandlerService::handleNotification(...,${context})");
    log.debug("${context.properties.headers}");

    try {
      def list_of_links = null
      // Json will be different if we have just 1 body.link - so wrap if needed
      if ( body.link instanceof List ) {
        list_of_links = body.link
      }
      else {
        list_of_links = [ body.link ]
      }
      

      list_of_links.each { link ->
        log.debug("Looking in link attribute for cap ${link}");

        // Different feeds behave differently wrt properly setting the type attribute. 
        // Until we get to grips a little better - try and parse every link - and if we manage to parse XML, see if the root node is a cap element
        if ( ( ( link.get('@type') != null ) && 
               ( 'application/cap+xml'.equals(link.get('@type')) ) ) ||
             ( true ) ) {

          try {
            def cap_link = link.'@href'

            log.debug("test ${cap_link}");
            java.net.URL cap_link_url = new java.net.URL(cap_link)
            java.net.URLConnection conn = cap_link_url.openConnection()
            
            log.debug("URL Connection reports content type ${conn.getContentType()}");

            if ( conn.getContentType().toLowerCase().startsWith('text/xml') ||
                 conn.getContentType().toLowerCase().startsWith('application/octet-stream') ||   // Because of http://www.gestiondelriesgo.gov.co
                 conn.getContentType().toLowerCase().startsWith('application/xml') ) {
              def parser = new XmlSlurper()
              parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false) 
              parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
              // def parsed_cap = parser.parse(cap_link)
              def parsed_cap = parser.parse(conn.getInputStream())
  
              //                      .declareNamespace(
              //                                        xmlschema:"http://www.w3.org/2001/XMLSchema",
              //                                        cap11:"urn:oasis:names:tc:emergency:cap:1.1",
              //                                        cap12:"urn:oasis:names:tc:emergency:cap:1.2")
          
              if ( parsed_cap.identifier ) {
                log.debug("Managed to parse link, looks like CAP :: handleNotification ::\"${parsed_cap.identifier}\"");
  
                def entry = domNodeToString(parsed_cap)
  
                // Render the cap object as JSON
                String json_text = capcollator.Utils.XmlToJson(entry);
  
                // http://www.nws.noaa.gov/geodata/ tells us how to understand geocode elements
    
                // Look at the various info.area elements - if the "polygon" element is null see if we can find an info.area.geocode we understand well enough to expand
  
                broadcastCapEvent(json_text, context.properties.headers)
              }
              else {
                log.warn("No valid CAP from ${cap_link} -- consider improving rules for handling this");
              }
            }
            else {
              log.warn("${cap_link} seems not to be XML and therefore cannot be a CAP message - skipping");
            }
          }
          catch ( Exception e ) {
            log.error("problem handling cap alert ${body} ${context} ${e.message}");
          }
        }
        else {
          log.error("Unable to find CAP link in ${body}");
        }
      }
    }
    catch ( Exception e ) {
      log.error("problem handling cap alert ${body} ${context} ${e.message}",e);
    }
  }

  def domNodeToString(node) {
    //Create stand-alone XML for the entry
    String xml_text =  groovy.xml.XmlUtil.serialize(node)
    xml_text
  }

  def broadcastCapEvent(json_event, headers) {
    try {
      def result = rabbitMessagePublisher.send {
              exchange = "CAPExchange"
              // headers = [
              //   'feed-id':feed_id,
              //   'entry-id':entry.id,
              //   'feed-url':entry.ownerFeed.baseUrl
              // ]
              routingKey = 'CAPAlert.'+(headers['feed-id'])
              body = json_event
      }
      log.debug("Result of Rabbit RPC publish: ${result}");
    }
    catch ( Exception e ) {
      log.error("Problem trying to publish to rabbit",e);
    }
  }
}
