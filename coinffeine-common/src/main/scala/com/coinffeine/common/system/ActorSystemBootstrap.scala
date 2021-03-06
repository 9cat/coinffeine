package com.coinffeine.common.system

import scala.util.control.NonFatal

import akka.actor.{Props, ActorSystem}

/** Bootstrap of an actor system whose supervisor actor is configured from CLI arguments and
  * whose termination stops the application.
  */
trait ActorSystemBootstrap {

  protected def supervisorProps(args: Array[String]): Props

  def main(args: Array[String]): Unit = {
    val system = ActorSystem("Main")
    try {
      val supervisor = system.actorOf(supervisorProps(args), "supervisor")
      system.actorOf(Props(classOf[akka.Main.Terminator], supervisor), "terminator")
    } catch {
      case NonFatal(ex) =>
        system.shutdown()
        throw ex
    }
  }
}
