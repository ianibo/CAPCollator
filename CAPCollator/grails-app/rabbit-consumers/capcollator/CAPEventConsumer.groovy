package capcollator

import com.budjb.rabbitmq.consumer.MessageContext

class CAPEventConsumer {

  def capEventHandlerService

  static rabbitConfig = [
    "exchange": "CAPExchange",
    "binding": "CAPAlert.#"
  ]

  def handleMessage(def body, MessageContext context) {
    capEventHandlerService.process(body);
  }
}