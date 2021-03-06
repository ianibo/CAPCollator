package capcollator

class AlertController {

  def index() { 
    log.debug("AlertController::index(${params})");
  }

  def nearby() {
    log.debug("AlertController::nearby(${params})");
    log.debug("accept: ${request.getHeader('ACCEPT')}");
  }

  def details() { 
    def result = [id:params.id]
    log.debug("AlertController::details(${params})");
    respond result
  }
}
