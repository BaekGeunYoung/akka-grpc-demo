package shopping.cart

import scala.concurrent.Future

import akka.actor.ActorSystem
import org.slf4j.LoggerFactory

class DemoHealthCheck(system: ActorSystem) extends (() => Future[Boolean]) {
  private val log = LoggerFactory.getLogger(getClass)

  override def apply(): Future[Boolean] = {
    log.info("DemoHealthCheck called")
    Future.successful(true)
  }
}
