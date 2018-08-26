package capcollator

import com.budjb.rabbitmq.consumer.MessageContext

class CAPSubscriptionMatchConsumer {

  def capEventHandlerService

  static rabbitConfig = [
    "exchange": "CAPExchange",
    "binding": "CAPSubMatch.#"
  ]

  def handleMessage(def body, MessageContext context) {
    log.debug("CAPSubscriptionMatchConsumer::handleMessage(${body},${context})");
  }
}
