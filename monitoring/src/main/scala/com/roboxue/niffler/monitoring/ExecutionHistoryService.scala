package com.roboxue.niffler.monitoring

import com.roboxue.niffler.execution.NifflerExceptionBase
import com.roboxue.niffler.{AsyncExecution, Niffler, Token}
import io.circe.{Encoder, Json}
import org.http4s.HttpService

import scala.util.{Failure, Success}

/**
  * @author rxue
  * @since 12/28/17.
  */
object ExecutionHistoryService extends Niffler with ServiceUtils {
  final val nifflerExecutionHistoryService: Token[HttpService] = Token("niffler execution history service")
  final val nifflerExecutionHistoryServiceTitle: Token[String] = Token("service title")
  final val nifflerExecutionHistoryServiceWrapper: Token[SubServiceWrapper] = Token("service wrapper")

  $$(nifflerExecutionHistoryServiceTitle.assign("Execution Service"))

  $$(nifflerExecutionHistoryService.dependsOn(nifflerExecutionHistoryServiceTitle) { (title) =>
    import io.circe.syntax._
    import org.http4s.circe.jsonEncoder
    import org.http4s.dsl._
    import org.http4s.twirl._
    HttpService {
      case GET -> Root =>
        Ok(html.nifflerExecutionHistory(title))
      case GET -> Root / "api" / "topology" / IntVar(executionId) =>
        val (liveExecutions, pastExecutions, _) = Niffler.getHistory
        (liveExecutions ++ pastExecutions).find(_.executionId == executionId) match {
          case Some(execution) =>
            val tokensWithDependencyDepth: Seq[(Set[Token[_]], Int)] =
              TopologyVertexRanker(execution.logic.topology, execution.forToken).zipWithIndex
            jsonResponse(Ok(Json.obj("topology" -> (for ((tokens, depth) <- tokensWithDependencyDepth) yield {
              Json.obj(
                "depth" -> Json.fromInt(depth),
                "tokens" -> tokens
                  .map(t => {
                    val dependentsUuid = execution.logic.getDependents(t).map(d => d.uuid)
                    val cachingPolicy = execution.logic.cachingPolicy(t)
                    tokenToJson(t).deepMerge(
                      Json
                        .obj("dependents" -> dependentsUuid.asJson, "cachingPolicy" -> cachingPolicy.toString.asJson)
                    )
                  })
                  .asJson
              )
            }).asJson)))
          case None =>
            NotFound()
        }
      case GET -> Root / "api" / "status" =>
        val (liveExecutions, pastExecutions, capacityRemaining) = Niffler.getHistory
        jsonResponse(
          Ok(
            Json.obj(
              "liveExecutions" -> liveExecutions.asJson,
              "pastExecutions" -> pastExecutions.asJson,
              "remainingCapacity" -> Json.fromInt(capacityRemaining)
            )
          )
        )
      case POST -> Root / "api" / "storageCapacity" / IntVar(capacity) if capacity > 0 =>
        Niffler.updateExecutionHistoryCapacity(capacity)
        Ok()
    }
  })

  $$(
    nifflerExecutionHistoryServiceWrapper
      .dependsOn(nifflerExecutionHistoryService, nifflerExecutionHistoryServiceTitle) { (service, title) =>
        SubServiceWrapper(title, "Lists all current and past executions, adjust history settings", "/history", service)
      }
  )

  $$(NifflerMonitor.nifflerMonitorSubServices.amendWithToken(nifflerExecutionHistoryServiceWrapper))

  implicit val tokenToJson: Encoder[Token[_]] = new Encoder[Token[_]] {
    override def apply(a: Token[_]): Json = {
      Json.obj(
        "name" -> Json.fromString(a.name),
        "codeName" -> Json.fromString(a.codeName),
        "returnType" -> Json.fromString(a.returnTypeDescription),
        "uuid" -> Json.fromString(a.uuid)
      )
    }
  }

  implicit val asyncExecutionToJson: Encoder[AsyncExecution[_]] = new Encoder[AsyncExecution[_]] {
    override def apply(a: AsyncExecution[_]): Json = {
      val base = Json.obj(
        "executionId" -> Json.fromInt(a.executionId),
        "token" -> Json.fromString(a.forToken.name),
        "tokenType" -> Json.fromString(a.forToken.returnTypeDescription),
        "startAt" -> Json.fromLong(a.triggeredTime),
        "state" -> Json.fromString(getExecutionState(a))
      )
      val extra = a.promise.future.value match {
        case None =>
          Json.Null
        case Some(Success(s)) =>
          Json.obj("finishAt" -> Json.fromLong(s.snapshot.asOfTime))
        case Some(Failure(ex: NifflerExceptionBase)) =>
          Json.obj(
            "exceptionMessage" -> Json.fromString(ex.getMessage),
            "stacktrace" -> Json.fromString(ex.toString),
            "finishAt" -> Json.fromLong(ex.snapshot.asOfTime)
          )
        case Some(Failure(otherEx)) =>
          Json.obj(
            "exceptionMessage" -> Json.fromString(otherEx.getMessage),
            "stacktrace" -> Json.fromString(otherEx.toString)
          )
      }
      base.deepMerge(extra)
    }
  }

  private def getExecutionState(a: AsyncExecution[_]): String = {
    a.promise.future.value match {
      case None             => "live"
      case Some(Success(_)) => "success"
      case Some(Failure(_)) => "failure"
    }
  }
}
