#!/bin/bash
export SDKMAN_DIR="/home/ibbo/.sdkman"
[[ -s "/home/ibbo/.sdkman/bin/sdkman-init.sh" ]] && source "/home/ibbo/.sdkman/bin/sdkman-init.sh"

export CC_VER=`grep appVersion ./CAPAggregator/gradle.properties | cut -f2 -d=`
echo Releasing CAPAggregator ${CC_VER}

sdk use grails 4.0.3
sdk use java 11.0.6.j9-adpt
cd CAPAggregator
grails prod war
cp build/libs/CAPAggregator-$CC_VER.war ../docker/CAPAggregator.war
cd ../docker
docker login

if [[ "$CC_VER" == *-SNAPSHOT ]]
then
  echo  SNAPSHOT release
  docker build -t semweb/caphub_aggregator:v$CC_VER -t semweb/caphub_aggregator:latest .
  docker push semweb/caphub_aggregator:v$CC_VER
  docker push semweb/caphub_aggregator:latest
else
  echo  Standard Release
  docker build -t semweb/caphub_aggregator:v$CC_VER -t semweb/caphub_aggregator:v2.0 -t semweb/caphub_aggregator:v2 -t semweb/caphub_aggregator:latest .
  docker push semweb/caphub_aggregator:v$CC_VER
  docker push semweb/caphub_aggregator:v2.1
  docker push semweb/caphub_aggregator:v2
  docker push semweb/caphub_aggregator:latest
fi


echo You can upgrade this on the live server with a command like
echo docker service update --image semweb/caphub_aggregator:v$CC_VER fah_capAggregator



