package com.roboxue.niffler.server

import akka.actor.{Actor, ActorRef}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.roboxue.niffler.Token
import com.roboxue.niffler.server.SessionActor.RequestEvaluation
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

class CoordinationActor(sessionId: Long, sessionActor: ActorRef, routingMap: Map[Token[_], String]) extends Actor {

  import context.dispatcher

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  val http = Http(context.system)

  override def receive: Receive = {
    case RequestEvaluation(`sessionId`, token, sessionContext, requestedAt, retryCount) =>
      val host = routingMap(token)
      http.singleRequest(HttpRequest(uri = host, method = HttpMethods.POST).withEntity(HttpEntity(
        ContentTypes.`application/json`,
        compact(
          ("session_id" -> sessionId) ~
            ("token" -> token.uuid) ~
            ("context" -> sessionContext.map(p => {
              ("token" -> p.token.uuid) ~
                ("value" -> p.valueToJValue)
            }))
        )))).map {
        case response @ HttpResponse(StatusCodes.OK, _, _, _) =>
          response.discardEntityBytes()
        case response @ HttpResponse(status, _, _, _) =>
          sessionActor ! SessionActor.SessionFailure(sessionId, token, status.toString(), System.currentTimeMillis())
          response.discardEntityBytes()

      }
  }
}
