package com.roboxue.niffler.monitoring

import com.roboxue.niffler.execution._
import com.roboxue.niffler.monitoring.utils.{DagTopologySorter, ServiceUtils}
import com.roboxue.niffler.syntax.{Constant, Requires}
import com.roboxue.niffler._
import fs2.time.awakeEvery
import fs2.{Scheduler, Sink, Strategy, Stream, Task}
import io.circe.syntax._
import io.circe.{Encoder, Json}
import org.http4s.circe.jsonEncoder
import org.http4s.dsl._
import org.http4s.twirl._
import org.http4s.{HttpService, Response}

import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * @author rxue
  * @since 12/28/17.
  */
object ExecutionHistoryService extends Niffler with ServiceUtils {
  final val nifflerExecutionHistoryService: Token[HttpService] = Token("niffler execution history service")
  final val nifflerExecutionHistoryServiceTitle: Token[String] = Token("execution history service title")
  final val nifflerExecutionHistoryServiceWrapper: Token[SubServiceWrapper] = Token("execution history service wrapper")

  $$(nifflerExecutionHistoryServiceTitle := Constant("Execution Service"))

  $$(nifflerExecutionHistoryService := nifflerExecutionHistoryServiceTitle.asFormula { (title) =>
    // thread pool used for socket streaming
    val scheduler: Scheduler = Scheduler.fromFixedDaemonPool(2, threadName = "socket")
    // thread pool used for calculating each frame in the socket stream
    val strategy: Strategy = Strategy.fromFixedDaemonPool(8, threadName = "worker")
    HttpService {
      case GET -> Root =>
        Ok(html.nifflerExecutionHistory(title))
      case GET -> Root / "api" / "execution" / IntVar(executionId) =>
        apiGetExecutionStatus(executionId)
      case GET -> Root / "api" / "status" =>
        apiGetNifflerStatus()
      case POST -> Root / "api" / "storageCapacity" / IntVar(capacity) if capacity > 0 =>
        apiUpdateCapacity(capacity)
    }
  })

  $$(
    nifflerExecutionHistoryServiceWrapper := Requires(
      nifflerExecutionHistoryService,
      nifflerExecutionHistoryServiceTitle
    ) { (service, title) =>
      SubServiceWrapper(title, "Lists all current and past executions, adjust history settings", "/history", service)
    }
  )

  $$(NifflerMonitor.nifflerMonitorSubServices += nifflerExecutionHistoryServiceWrapper.asFormula)

  private def apiGetExecutionStatus(executionId: Int): Task[Response] = {
    val (liveExecutions, pastExecutions, _) = NifflerRuntime.getHistory
    (liveExecutions ++ pastExecutions).find(_.executionId == executionId) match {
      case Some(execution) =>
        jsonResponse(Ok(asyncExecutionDetailsToJson(execution).spaces2))
      case None =>
        NotFound()
    }
  }

  private def apiGetNifflerStatus(): Task[Response] = {
    val (liveExecutions, pastExecutions, capacityRemaining) = NifflerRuntime.getHistory
    jsonResponse(
      Ok(
        Json.obj(
          "liveExecutions" -> liveExecutions.asJson,
          "pastExecutions" -> pastExecutions.reverse.asJson,
          "remainingCapacity" -> capacityRemaining.asJson
        )
      )
    )
  }

  private def apiUpdateCapacity(newCapacity: Int): Task[Response] = {
    NifflerRuntime.updateExecutionHistoryCapacity(newCapacity)
    Ok()
  }

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
        "startAt" -> a.getExecutionSnapshot.invocationTime.asJson,
        "state" -> Json.fromString(a.promise.future.value match {
          case None                              => "live"
          case Some(Success(_))                  => "success"
          case Some(Failure(_)) if a.isCancelled => "cancelled"
          case Some(Failure(_))                  => "failure"
        })
      )
      val extra = a.promise.future.value match {
        case Some(Success(s)) =>
          Json.obj("finishAt" -> Json.fromLong(s.snapshot.asOfTime))
        case Some(Failure(ex: NifflerEvaluationException)) =>
          Json.obj(
            "exceptionMessage" -> Json.fromString(ex.getMessage),
            "stacktrace" -> Json.fromString(ex.toString),
            "finishAt" -> Json.fromLong(ex.snapshot.asOfTime),
            "tokenWithException" -> ex.tokenWithException.uuid.asJson
          )
        case Some(Failure(ex: NifflerInvocationException)) =>
          Json.obj(
            "exceptionMessage" -> Json.fromString(ex.getMessage),
            "stacktrace" -> Json.fromString(ex.toString),
            "finishAt" -> Json.fromLong(ex.snapshot.asOfTime),
            "tokensMissingImpl" -> ex.tokensMissingImpl.map(_.uuid).asJson
          )
        case Some(Failure(ex: NifflerTimeoutException)) =>
          Json.obj(
            "exceptionMessage" -> Json.fromString(ex.getMessage),
            "stacktrace" -> Json.fromString(ex.toString),
            "finishAt" -> Json.fromLong(ex.snapshot.asOfTime),
            "timeout" -> ex.timeout.toMillis.asJson
          )
        case _ =>
          Json.obj()
      }
      base.deepMerge(extra)
    }
  }

  private def asyncExecutionDetailsToJson: Encoder[AsyncExecution[_]] = new Encoder[AsyncExecution[_]] {
    override def apply(a: AsyncExecution[_]): Json = {
      val snapshot = a.getExecutionSnapshot
      val tokensByLayer: Seq[Set[Token[_]]] = DagTopologySorter(snapshot.logic.dag, snapshot.tokenToEvaluate)
      asyncExecutionToJson(a).deepMerge({
        val dag = (for (tokens <- tokensByLayer) yield {
          val tokensJson = tokens
            .map(t => {
              val prerequisitesUuid = snapshot.logic.getPredecessors(t).map(d => d.uuid)
              val cachingPolicy = snapshot.logic.cachingPolicy(t)
              tokenToJson(t).deepMerge(
                Json
                  .obj("prerequisites" -> prerequisitesUuid.asJson, "cachingPolicy" -> cachingPolicy.toString.asJson)
              )
            })
            .asJson
          Json.obj("tokens" -> tokensJson)
        }).asJson
        val timelineEvents = (for (event <- snapshot.timelineEvents) yield {
          Json
            .obj("uuid" -> event.token.uuid.asJson, "time" -> event.time.asJson, "eventType" -> event.eventType.asJson)
            .deepMerge(event match {
              case TimelineEvent.EvaluationCancelled(_, _, reason) =>
                Json.obj("reason" -> reason.asJson)
              case TimelineEvent.EvaluationFailed(_, _, ex) =>
                Json.obj("exceptionMessage" -> ex.getMessage.asJson)
              case _ =>
                Json.obj()
            })
        }).asJson
        Json.obj(
          "targetToken" -> tokenToJson(snapshot.tokenToEvaluate),
          "dag" -> dag,
          "asOfTime" -> snapshot.asOfTime.asJson,
          "timelineEvents" -> timelineEvents
        )
      })
    }
  }
}
