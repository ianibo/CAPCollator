# About

CAPCollator is a component in a service oriented middleware system that bridges the gap between the diverse set of Common Alerting 
Protocol (CAP) Alert Producers and systems which wish
to consume alerts that might be applicable in a specific place and with specific critera (EG Official Only, High Priority, Matching keywords). 
It runs on a server, usually in a docker-swarm orchestrated cluster, and harvests alerts from a configured collection of sources
in order to maintain a comprehensive and searchable global index of all currently live CAP alerts, regardless of source.

A demo system is available at [http://demo.semweb.co/CAPCollator](https://demo.semweb.co/CAPCollator)

This work has been kindly supported by UCAR Subaward SUBAWD001770



## Technology

Currently implemented in Java using the groovy-on-grails framework, runnable as a microservice using the
production jar or as a docker container [https://hub.docker.com/repository/docker/semweb/caphub_aggregator](see Dockerhub) . An embedded database is used for feed state. Elasticsearch 7 is used for geo indexing, RabbitMQ is used for
message fanout and durable delivery. There have been experiments running the service under kubernetes/OpenShift - contact @ianibo for info.

## Deployment

There are many deployment options, the initial goal was for a local facade, but projects may also use the
service internally to poll RSS and turn feeds into event streams.

### GeneralUser

    CREATE USER capcollator WITH PASSWORD 'capcollator';

### Dev Env

    DROP DATABASE capcollatordev;
    CREATE DATABASE capcollatordev;
    GRANT ALL PRIVILEGES ON DATABASE capcollatordev to capcollator;

### Production

    DROP DATABASE capcollatorprod;
    CREATE DATABASE capcollatorprod;
    GRANT ALL PRIVILEGES ON DATABASE capcollatorprod to capcollator;



### Gaz data

See http://eric.clst.org/Stuff/USGeoJSON
http://www.nws.noaa.gov/geodata/catalog/wsom/html/pubzone.htm



# Installation

In order to work fully, the ES mappings need to be installed. run the es5_config.sh script from the scripts directory BEFORE running


## ES Queries

http://localhost:9200/alertssubscriptions/alertsubscription/_search?q=*

## RabbitMQ

http://localhost:15672/#/ -- mgt interface
http://localhost:15670/ -- WebSockents info

To try and get an idea of rabbit throughput, visit the mgt interface on port 15672 and log in as the cap admin user - using the dev setup this is cap/cap


## Apache2

CAPCollator makes it's events available over websockets. This is done via rabbitmq. The following stanza can be
used to proxy the rabbitmq web_stomp plugin in front of apache for easy connectivity.

      AllowEncodedSlashes On
      <LocationMatch "/rabbitws/">
        ProxyPass http://localhost:15674/ nocanon
        ProxyPassReverse http://localhost:15674/
      </LocationMatch>

## Debugging

The rabbit MQ mgt interface (guest/guest) on http://localhost:15672/#/ is a good place to look for messages.

## Deployment

CAPCollator can track usage using google analytics. Add your GTM Code to TOMCAT_HOME/conf/Catalina/localhost/CAPCollator.xml. Example under src/main/webapp/META-INF/context.xml or as follows

    <Context path="/CAPCollator" reloadable="false">
      <Parameter name="gtmcode" value="GTM-56M9D5W" override="false"/>
    </Context>



Example ATOM feed

https://alert-feeds.s3.amazonaws.com/unfiltered/rss.xml
