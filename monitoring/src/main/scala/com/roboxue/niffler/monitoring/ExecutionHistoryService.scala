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
object ExecutionHistoryService extends Niffler {
  final val nifflerExecutionHistoryService: Token[HttpService] = Token("niffler execution history service")
  final val nifflerExecutionHistoryServiceTitle: Token[String] = Token("service title")
  final val nifflerExecutionHistoryServiceWrapper: Token[SubServiceWrapper] = Token("service wrapper")

  $$(nifflerExecutionHistoryServiceTitle.assign("Execution Service"))

  $$(nifflerExecutionHistoryService.dependsOn(nifflerExecutionHistoryServiceTitle) { (title) =>
    import org.http4s.dsl._
    import org.http4s.twirl._ // render twirl templates (Html)
    import org.http4s.circe.jsonEncoder // render circe json (Json)
    import io.circe.syntax._
    HttpService {
      case GET -> Root =>
        Ok(html.nifflerExecutionHistory(title))
      case GET -> Root / IntVar(executionId) =>
        //
        Ok(html.nifflerExecutionHistory(title))
      case GET -> Root / "api" / "status" =>
        val liveExecutions = Niffler.getLiveExecutions
        val (pastExecutions, capacityRemaining) = Niffler.getExecutionHistory
        Ok(
          Json.obj(
            "liveExecutions" -> liveExecutions.asJson,
            "pastExecutions" -> pastExecutions.asJson,
            "remainingCapacity" -> Json.fromInt(capacityRemaining)
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

  implicit val asyncExecutionToJson: Encoder[AsyncExecution[_]] = new Encoder[AsyncExecution[_]] {
    override def apply(a: AsyncExecution[_]): Json = {
      val base = Json.obj(
        "executionId" -> Json.fromInt(a.executionId),
        "token" -> Json.fromString(a.forToken.name),
        "tokenType" -> Json.fromString(a.forToken.returnTypeDescription),
        "startAt" -> Json.fromLong(a.triggeredTime)
      )
      val extra = a.promise.future.value match {
        case None =>
          Json.obj("state" -> Json.fromString("live"))
        case Some(Success(s)) =>
          Json.obj("state" -> Json.fromString("success"), "finishAt" -> Json.fromLong(s.snapshot.asOfTime))
        case Some(Failure(ex: NifflerExceptionBase)) =>
          Json.obj(
            "state" -> Json.fromString("failure"),
            "exceptionMessage" -> Json.fromString(ex.getMessage),
            "stacktrace" -> Json.fromString(ex.toString),
            "finishAt" -> Json.fromLong(ex.snapshot.asOfTime)
          )
      }
      base.deepMerge(extra)
    }
  }
}
