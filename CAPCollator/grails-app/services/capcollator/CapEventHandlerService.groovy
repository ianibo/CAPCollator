package capcollator

import grails.transaction.Transactional
import com.budjb.rabbitmq.publisher.RabbitMessagePublisher
import java.util.Iterator
import static groovy.json.JsonOutput.*


@Transactional
class CapEventHandlerService {

  RabbitMessagePublisher rabbitMessagePublisher
  def ESWrapperService
  def eventService
  def gazService
  def alertFetcherExecutorService

  // import org.apache.commons.collections4.map.PassiveExpiringMap;
  // Time to live in millis - 1000 * 60 == 1m 
  // private Map geo_query_cache = Collections.synchronizedMap(new PassiveExpiringMap(1000*60))
  

  static final int queue_size = 0;

  /**
   * Fired when we have detected a CAP event, to capture the event and index it in our local ES index
   */
  def process(cap_notification) {
    // break out this call here as this needs to be convered into an executor pool, this handler is
    // becomming a bottleneck in processing as alerts with high numbers of areas and high numbers of
    // vertices can slow down processing
    queue_size++;
    log.debug("CapEventHandlerService::process - enqueue - size is ${queue_size}");
    alertFetcherExecutorService.submit({
      internalProcess(cap_notification)
    } as java.lang.Runnable )
  }


  def internalProcess(cap_notification) {

    queue_size--;
    long start_time = System.currentTimeMillis();

    log.debug("CapEventHandlerService::process (queue_size :: ${queue_size}, alert from ${cap_notification.AlertMetadata.sourceFeed})"); 

    try {
      def cap_body = cap_notification.AlertBody
      def polygons_found=0

      if ( cap_notification.AlertMetadata.tags == null ) {
        cap_notification.AlertMetadata.tags=[]
      } 

      if ( cap_notification.AlertMetadata['warnings'] == null ) {
        cap_notification.AlertMetadata['warnings'] = []
      }

      // This is a little ugly, but pull the event time up to the root of the document, so that we have something we know we
      // can sort by without needing a nested query
      if ( cap_body.sent != null ) {
        cap_notification.evtTimestamp = cap_body.sent;
      }

      Map geo_query_cache = [:]

      // Extract any shapes from the cap (info) alert['alert']['info'].each { it.['area'] }
      if ( cap_body?.info ) {
        def list_of_info_elements = cap_body.info instanceof List ? cap_body.info : [ cap_body.info ]

        // Create a set - this will prevent duplicate subscriptions if multiple info elements match
        def matching_subscriptions = new java.util.HashSet()

        list_of_info_elements.each { ie ->

          long query_phase_start_time = System.currentTimeMillis();

          log.debug("  -> Check info element");
          if ( ie.area ) {
            def list_of_area_elements = ie.area instanceof List ? ie.area : [ ie.area ]

            list_of_area_elements.each { area ->

              log.debug("Processing area...");

              if ( area.cc_polys == null ) {
                area.cc_polys = [];
              }

              if ( area.polygon != null ) {
  
                if ( !cap_notification.AlertMetadata.tags.contains('AREATYPE_POLYGON') ) 
                  cap_notification.AlertMetadata.tags.add('AREATYPE_POLYGON');

                def list_of_polygon_elements = area.polygon instanceof List ? area.polygon : [ area.polygon ]
  
                log.debug("....As polygon.. count=${list_of_polygon_elements.size()}");

                list_of_polygon_elements.each { poly_elem ->

                  polygons_found++
                  // We got a polygon
                  def inner_polygon_ring = geoJsonToPolygon(poly_elem)
                  def match_result = matchSubscriptionPolygon(geo_query_cache,inner_polygon_ring)

                  matching_subscriptions.addAll(filterNonGeoProperties(match_result.subscriptions, cap_notification));

                  cap_notification.AlertMetadata['warnings'].addAll(match_result.messages);

                  if ( match_result.status == 'ERROR' ) {
                    cap_notification.AlertMetadata.tags.add('GEO_SEARCH_ERROR');
                  }
  
                  // We enrich the parsed JSON document with a version of the polygon that ES can index to make the whole
                  // database of alerts geo searchable
                  area.cc_polys.add( [ type:'polygon', coordinates:[ inner_polygon_ring ] ] );
                }
  
                // If we got a polygon AND there was an info.area.geocode then we can look to see if we should cache that code
                // this is duff -- an alert can have many polygons and many geocodes, so the assumption here is wrong. 
                if ( 1==2 ) {
                  if ( area.geocode && area.geocode.value && area.geocode.valueName ) {
                    log.debug("CAP Alert contains polygon and geocode - cache value - ${area.geocode}");
                    def authorities = area.geocode.valueName instanceof List ? area.geocode.valueName : [ area.geocode.valueName ]
                    def symbols = area.geocode.value instanceof List ? area.geocode.value : [ area.geocode.value ]
    
                    Iterator i1=authorities.iterator()
                    Iterator i2=symbols.iterator()
                    for (; i1.hasNext() && i2.hasNext(); ) {
                      
                      try {
                        gazService.cache(i1.next(), i2.next(), inner_polygon_ring);
                      }
                      catch ( Exception e ) {
                        log.error("problem trying to cache gaz entry",e);
                      }
                    }
                  }
                }
  
                area.geocode?.each { gc ->
                  log.debug("CAP Alert has geocode : ${gc} ");
                }
              }

              if ( area.circle != null ) {

                log.debug("....As circle");

                if ( !cap_notification.AlertMetadata.tags.contains('AREATYPE_POLYGON') ) 
                  cap_notification.AlertMetadata.tags.add('AREATYPE_CIRCLE');

                def list_of_circle_elements = area.circle instanceof List ? area.circle : [ area.circle ]

                list_of_circle_elements.each { circle ->
                  polygons_found++
  
                  log.debug("Area defines a circle"); // EG 2.58,-70.06 13
                  def coords_radius = circle.split(' ');
                  def coords = null;
                  def radius = null;
  
                  if ( coords_radius.length > 0 ) {
                    coords = coords_radius[0].split(',');
                  }
  
                  if ( coords_radius.length > 1 ) {
                    // CAP Standard determies that radius must be given in KM, so no conversion needed.
                      // LEFT HERE FOR INFO:: THIS IS NOT THE CASE NOW Got radius part - We assume the feed is giving a radius in miles, so we convert to ES KM here // radius = Integer.parseInt(coords_radius[1]) * 1.6;
                    radius = Float.parseFloat(coords_radius[1])
                  }
                  else {
                    log.debug("Using default radius of 10 KM");
                    radius = 10
                  }
  
                  if ( coords != null ) {
                    log.debug("Parse coordinates - we assume <circle> elements are composed within the circle element as lat <comma> lon <space> radius : ${circle}");
                    def lat = Float.parseFloat(coords[0]);  // -90 to +90
                    def lon = Float.parseFloat(coords[1]);  // -180 to +180
                    def match_result = matchSubscriptionCircle(lat,lon,radius,cap_notification)
                    matching_subscriptions.addAll(filterNonGeoProperties(match_result.subscriptions, cap_notification));
                    // matching_subscriptions.addAll(match_result.subscriptions);
  
                    cap_notification.AlertMetadata['warnings'].addAll(match_result.messages);
  
                    if ( match_result.status == 'ERROR' ) {
                      cap_notification.AlertMetadata.tags.add('GEO_SEARCH_ERROR');
                    }
    
                    // We enrich the parsed JSON document with a version of the polygon that ES can index to make the whole
                    // database of alerts geo searchable
                    area.cc_polys.add(  [ type:'circle', coordinates:[ coords[1], coords[0] ], radius:"${radius}km" ] )
                  }
                  else {
                    log.error("Failed to parse circle area ${circle}");
                  }
                }
              }
            }
          }
          log.info("info element checking complete. QueryPhase elapsed: ${System.currentTimeMillis() - query_phase_start_time}");
        }

        log.debug("The following subscriptions matched : ${matching_subscriptions} (# polygons found:${polygons_found})");

        if ( polygons_found == 0 ) {
          eventService.registerEvent('CAPXMLWithNoPolygon',System.currentTimeMillis());
          cap_notification.AlertMetadata.tags.add('No_Polygon_Provided');
        }

        cap_notification.AlertMetadata.CCHistory.add(['event':'CC-spatial-processing-complete','timestamp':System.currentTimeMillis()]);
        publishAlert(cap_notification, matching_subscriptions);

        // Index the CAP event
        indexAlert(cap_notification, matching_subscriptions)
      }

    }
    catch ( Exception e ) {
      log.debug("CapEventHandlerService::internalProcess Exception processing CAP notification:\n${cap_notification}\n",e);
    }
    finally {
      log.info("CapEventHandlerService::internalProcess complete elapsed=${System.currentTimeMillis() - start_time}");
    }

  }

  def publishAlert(cap_notification, matching_subscriptions) {
    log.debug("Publishing CAPSubMatch. notifications");
    matching_subscriptions.each { sub_id ->

      // Apply other subscription filters
      try {
        if ( criteriaMet(sub_id, cap_notification) ) {
          log.debug("Publishing CAPSubMatch.${sub_id} notification");
          def result = rabbitMessagePublisher.send {
                exchange = "CAPExchange"
                routingKey = 'CAPSubMatch.'+sub_id
                body = cap_notification
          }
        }
      }
      catch ( Exception e ) {
        log.error("Problem trying to publish to rabbit",e);
      }
    }
  }

  private boolean criteriaMet(String sub_id, Map cap_notification) {
    return true;
  }

  def indexAlert(cap_notification, matching_subscriptions) {
    if ( cap_notification.AlertMetadata ) {
      // Store the matching subscriptions in the metadata
      cap_notification.AlertMetadata['MatchedSubscriptions']=matching_subscriptions
      // Drop the signature -- it's very verbose and applies to the underlying XML document. 
      // Consumers should return the source CAP if they want to validate the alert
      cap_notification.AlertBody.Signature=null
      if ( cap_notification.AlertBody.identifier != null ) {
        ESWrapperService.index('alerts', 'alert', "${cap_notification.AlertBody.identifier}".toString(), cap_notification)
      }
      else {
        ESWrapperService.index('alerts', 'alert', cap_notification)
      }
    }
  }

  private List geoJsonToPolygon(String polygon_ring_string) {


    // Polygon as given is a ring list of space separated pairs - "x1,y1 x2,y2 x3,y3 x4,y4 x1,y1"
    List polygon_ring = []

    def last_pair = null
    // def cleaned_polygon_ring_string = polygon_ring_string.replaceAll('\\s+',' ');
    def cleaned_polygon_ring_string = polygon_ring_string.replaceAll('\n',' ');
    def list_of_pairs = cleaned_polygon_ring_string.split(' ')
    list_of_pairs.each { coordinate_pair ->
      // geohash wants lon,lat the other way to our geojson, so flip them
      def split_pair = coordinate_pair.split(',')
      if ( split_pair.size() == 2 ) {
        if ( ( last_pair != null ) && ( ( last_pair[0] == split_pair[0] ) && ( last_pair[1] == split_pair[1] ) ) ) {
          log.debug("Skipping repeated pair of coordinates ${split_pair}");
        }
        else {
          polygon_ring.add([split_pair[1],split_pair[0]])
          last_pair = split_pair
        }
      }
      else {
        log.error("Problem attempting to split coordiate pair ${coordinate_pair}");
      }
    }

    return polygon_ring
  }


  /**
   * Find all subscriptions which overlap with the supplied polygon ring
   * having obtained the list, check non-spatial filter properties
   */
  def matchSubscriptionPolygon(geo_query_cache,polygon_ring) {

    log.debug("matchSubscriptionPolygon(...)");

    String poly_str = polygon_ring.toString();

    def result=geo_query_cache.get(poly_str)

    if ( result ) {
      return result;
    }
    else {
      result = [
        subscriptions:null,
        messages:[],
        status:'OK'
      ]
    }

    String query = '''{
         "bool": {
           "must": {
             "match_all": {}
           },
           "filter": {
               "geo_shape": {
                 "subshape": {
                   "shape": {
                     "type": "polygon",
                     "coordinates":['''+polygon_ring+''']
                   },
                   "relation":"intersects"
                 }
               }
             }
           }
         }'''


    String[] indexes_to_search = [ 'alertssubscriptions' ]
    try {
      def matching_subs = ESWrapperService.search(indexes_to_search,query);

      if ( matching_subs ) {
        result.subscriptions = matching_subs.getHits().getHits();
        // matching_subs.getHits().getHits().each { matching_sub ->
        //   result.subscriptions.add(sub_as_map.shortcode)
        // }
      }
    }
    catch ( Exception e ) {
      result.messages.add(e.message+'\n'+query);
      result.status='ERROR';
      log.error("SEARCH ERROR:: Validate with\ncurl -X GET 'http://eshost:9200/alertssubscriptions/_search' -d ${query}")
    }

    geo_query_cache.put(poly_str,result)

    result
  }

  private List filterNonGeoProperties(org.elasticsearch.search.SearchHit[] matching_subscriptions, Map cap_notification) {
    List result = []
    matching_subscriptions?.each { matching_sub ->
      Map sub_as_map = matching_sub.sourceAsMap();
      if ( passNonSpatialFilter(sub_as_map, cap_notification) ) {
        result.add(sub_as_map.shortcode)
      }
    }

    log.debug("filterNonGeoProperties called with list of ${matching_subscriptions.size()} returns list of ${result.size()}");
    return result;
  }

  // def matchSubscriptionCircle(centre, radius) {
  // "coordinates":['''+centre[1]+''','''+centre[0]+'''],
  def matchSubscriptionCircle(float lat, float lon, float radius, Map cap_notification) {

    log.debug("matchSubscriptionCircle(${lat},${lon},${radius})");

    def result=[
      subscriptions:[],
      messages:[],
      status:'OK'
    ]

    // ES shape points accept Geo-point expressed as an array with the format: [ lon, lat] or a string "lat,lon"
    String query = '''{
         "bool": {
           "must": {
             "match_all": {}
           },
           "filter": {
               "geo_shape": {
                 "subshape": {
                   "shape": {
                     "type": "circle",
                     "coordinates":['''+lon+''','''+lat+'''],
                     "radius": "'''+radius+'''km"
                   },
                   "relation":"intersects"
                 }
               }
             }
           }
         }'''


    String[] indexes_to_search = [ 'alertssubscriptions' ]
    try {
      def matching_subs = ESWrapperService.search(indexes_to_search,query);

      if ( matching_subs ) {
        result.subscriptions = matching_subs.getHits().getHits();
      }
    }
    catch ( Exception e ) {
      result.messages.add(e.message+'\n'+query);
      log.error("Problem trying to match circle::${e.message}",e);
      result.status='ERROR';
      log.error("matchSubscriptionCircle ERROR: Validate with\ncurl -X GET 'http://eshost:9200/alertssubscriptions/_search' -d ${query}")
    }

    result
  }

  private boolean passNonSpatialFilter(Map subscription, Map cap_notification) {
    boolean result = true;

    log.debug("checking non-spatial filter settings for ${subscription.shortcode} ${subscription}");
    log.debug("Testing ${subscription.languageOnly}");
    if ( subscription.languageOnly ) {
      log.debug("Check for language...${subscription.languageOnly}");
    }

    // (//cap:urgency='Immediate' or //cap:urgency='Expected') and (//cap:severity='Extreme' or //cap:severity='Severe') and (//cap:certainty='Observed' or //cap:certainty='Likely')
    log.debug("Testing ${subscription.highPriorityOnly}");
    if ( subscription.highPriorityOnly ) {
      log.debug("Filter high priority only ${true}");
      // If ( urgency==immediate || urgency==expected ) && ( severity==extreme || severity==severe ) && ( certainty==observed || certainty==likely )
      // if ( cap_notification.AlertBody.info.urgency ) (cap_notification.AlertBody.info.severity)   cap_notification.AlertBody.info.certainty
    }

    log.debug("Testing ${subscription.officialOnly}");
    if ( subscription.officialOnly ) {
      log.debug("Filter official priority only");
      if ( cap_notification.AlertMetadata.sourceIsOfficial.equalsIgnoreCase('true') ) {
        log.debug("pass - source is official");
      }
      else {
        log.debug("fail - source is official");
        result = false;
      }
    }

    log.debug("Testing ${subscription.xPathFilterId}");
    if ( subscription.xPathFilterId ) {
      log.debug("Apply xpath filter ${PathFilterId}")
    }

    log.debug("passNonSpatialFilter returns ${result}");

    return result;
  }

  def matchAlertCircle(float lat, float lon, float radius) {

    def result = [messages:[]];
    log.debug("matchAlertCircle(${lat},${lon},${radius})");

    // ES shape points accept Geo-point expressed as an array with the format: [ lon, lat] or a string "lat,lon"
    String query = '''{
         "bool": {
           "must": {
             "match_all": {}
           },
           "filter": {
             "geo_shape": {
               "AlertBody.info.area.cc_polys": {
                 "shape": {
                   "type": "circle",
                   "coordinates":['''+lon+''','''+lat+'''],
                   "radius": "'''+radius+'''km"
                 },
                 "relation":"intersects"
               }
             }
           }
         }
       }'''

    log.debug("Validate with\ncurl -X GET 'http://host-of-es/alerts/_search' -d ${query}")

    String[] indexes_to_search = [ 'alerts' ]
    try {
      result.alerts = ESWrapperService.search(indexes_to_search,query);
      result.status='OK';
    }
    catch ( Exception e ) {
      log.error("Problem trying to match circle::${e.message}",e);
      result.messages.add(e.message);
      result.status='ERROR';
    }

    result
  }

  
}
