package controllers

import play.api._
import libs.json.{JsValue, Reads, JsPath, Json}
import libs.ws.WS
import play.api.mvc._
import scala.concurrent.ExecutionContext.Implicits.global
import models.Lotto

object Application extends Controller {

  def index = Action {
    implicit request => Ok(views.html.index())
  }


  def lotto() = WebSocket.async[JsValue] { request  =>

    Lotto.join()

  }


  def stop() = Action {

    Lotto.stop

    Ok("OK")
  }


}