package models

import akka.util.Timeout
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.libs.json._

import play.api.libs.iteratee._
import play.api.libs.concurrent._
import akka.util.Timeout
import play.api.Play.current
import concurrent.{Await, Future, ExecutionContext}
import ExecutionContext.Implicits.global

import akka.pattern.ask

import akka.actor._
import scala.concurrent.duration._
import play.api.libs.ws.WS
import play.api.{Play, Logger}
import scala.util.Random
import play.api.libs.json.JsString

object Meetup {

  @volatile var cancel:Cancellable = null

  def apply(f:Seq[JsValue] => Unit) {

    val requestUrl = "https://api.meetup.com/2/rsvps?key="+ Play.configuration.getString("meetup.api.key").getOrElse("yourkey") + "&sign=true&rsvp=yes&event_id=107876972&page=50"
    val response = WS.url(requestUrl).get()

    response.map { json =>
          (json.json \ "results").as[Seq[JsObject]].map { a =>
            Json.obj(
              "name" -> (a \ "member" \ "name").as[String],
              "photo" -> (a \ "member_photo" \ "photo_link").as[String]
            )
          }
  }.map { members =>
      implicit val timeout = Timeout(1 second)
      Logger.info("~" + members.toString())
      cancel = Akka.system.scheduler.schedule(
        0 seconds,
        2 seconds
      )(f(members))
    }
  }


  def stop = {
    cancel.cancel()
  }
}


object Lotto {

  implicit val timeout = Timeout(1 second)


  lazy val default = {
    val lottoActor = Akka.system.actorOf(Props[Lotto])
    Meetup(x => lottoActor ! Send(x))
    lottoActor
  }


  def join(): scala.concurrent.Future[(Iteratee[JsValue, _], Enumerator[JsValue])] = {

    (default ? Ok).map {
      case Connected(enumerator) =>

        val iteratee = Iteratee.ignore[JsValue]

        (iteratee, enumerator)

      case Stop() =>
        val iteratee = Done[JsValue,Unit]((),Input.EOF)
        val enumerator =  Enumerator[JsValue]().andThen(Enumerator.enumInput(Input.EOF))

        (iteratee,enumerator)

    }
  }

  def stop = {
    Akka.system.scheduler.scheduleOnce(Random.nextInt(30) seconds, default, Stop)
 }

}


class Lotto extends Actor {

  val (lottoEnumerator, channel) = Concurrent.broadcast[JsValue]

  def receive = {

    case Ok => {
      sender ! Connected(lottoEnumerator)
    }

    case Stop => {
      Meetup.stop
      channel.eofAndEnd()
    }

    case Send(members) => {
      val name = members(Random.nextInt(49))
      Logger.info("send " + (name \ "name").as[String])

      channel.push(name)
    }
  }

}

case class Ok()
case class Stop()
case class Send(members: Seq[JsValue])
case class Connected(enumerator:Enumerator[JsValue])
