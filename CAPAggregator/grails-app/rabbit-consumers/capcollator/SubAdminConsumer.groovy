package capcollator

import com.budjb.rabbitmq.consumer.MessageContext

class SubAdminConsumer {

  def grailsApplication
  def staticFeedService

  static rabbitConfig = [
    "exchange": "CAPExchange",
    "binding": "CAPSubAdmin.#",
    "transacted": true
  ]

  def handleMessage(def body, MessageContext context) {
    log.info("SubAdminConsumer::handle - key = ${context.envelope.routingKey}");
    String[] key_components = context.envelope.routingKey.split('\\.');
    if ( key_components.length == 2 ) {
      String tmpl = body.feedXmlTemplate;
      staticFeedService.initialiseFeed(key_components[1], tmpl);
    }
    else {
      log.warn("Unexpected number of components (${key_components.length}) in routing key for CAPSubAdmin event: ${key_components}");
    }
  }
}
