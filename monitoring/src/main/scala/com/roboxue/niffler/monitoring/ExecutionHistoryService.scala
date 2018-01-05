package com.roboxue.niffler.monitoring

import com.roboxue.niffler.execution._
import com.roboxue.niffler.{AsyncExecution, Niffler, Token}
import fs2.time.awakeEvery
import fs2.{Scheduler, Sink, Strategy, Stream, Task}
import io.circe.syntax._
import io.circe.{Encoder, Json}
import org.http4s.circe.jsonEncoder
import org.http4s.dsl._
import org.http4s.server.websocket.WS
import org.http4s.twirl._
import org.http4s.websocket.WebsocketBits.{Text, WebSocketFrame}
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

  $$(nifflerExecutionHistoryServiceTitle.assign("Execution Service"))

  $$(nifflerExecutionHistoryService.dependsOn(nifflerExecutionHistoryServiceTitle) { (title) =>
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
      case GET -> Root / "api" / "executionStream" / IntVar(executionId) =>
        apiGetExecutionStatusAsStream(executionId)(scheduler, strategy)
      case POST -> Root / "api" / "storageCapacity" / IntVar(capacity) if capacity > 0 =>
        apiUpdateCapacity(capacity)
    }
  })

  $$(
    nifflerExecutionHistoryServiceWrapper
      .dependsOn(nifflerExecutionHistoryService, nifflerExecutionHistoryServiceTitle) { (service, title) =>
        SubServiceWrapper(title, "Lists all current and past executions, adjust history settings", "/history", service)
      }
  )

  $$(NifflerMonitor.nifflerMonitorSubServices.amendWithToken(nifflerExecutionHistoryServiceWrapper))

  private def apiGetExecutionStatus(executionId: Int): Task[Response] = {
    val (liveExecutions, pastExecutions, _) = Niffler.getHistory
    (liveExecutions ++ pastExecutions).find(_.executionId == executionId) match {
      case Some(execution) =>
        jsonResponse(Ok(asyncExecutionDetailsToJson(execution).spaces2))
      case None =>
        NotFound()
    }
  }

  private def apiGetExecutionStatusAsStream(executionId: Int)(implicit scheduler: Scheduler,
                                                              strategy: Strategy): Task[Response] = {
    val (liveExecutions, pastExecutions, _) = Niffler.getHistory
    (liveExecutions ++ pastExecutions).find(_.executionId == executionId) match {
      case Some(execution) =>
        val replyInitialStatus = Stream.emit(Text(asyncExecutionDetailsToJson(execution).noSpaces))
        val toClient: Stream[Task, WebSocketFrame] = replyInitialStatus ++ awakeEvery[Task](1.seconds).map { _ =>
          Text(asyncExecutionDetailsToJson(execution).noSpaces)
        }.takeWhile(_ => {
          !execution.promise.isCompleted
        })
        val discardClientMessages: Sink[Task, WebSocketFrame] = { (clientStream) =>
          clientStream.evalMap[Task, Task, Unit](_ => Task.delay(Unit))
        }
        WS(toClient, discardClientMessages)
      case None =>
        NotFound()
    }
  }

  private def apiGetNifflerStatus(): Task[Response] = {
    val (liveExecutions, pastExecutions, capacityRemaining) = Niffler.getHistory
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
    Niffler.updateExecutionHistoryCapacity(newCapacity)
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
          case None             => "live"
          case Some(Success(_)) => "success"
          case Some(Failure(_)) => "failure"
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
      val tokensByLayer: Seq[Set[Token[_]]] = TopologyVertexRanker(snapshot.logic.topology, snapshot.tokenToEvaluate)
      asyncExecutionToJson(a).deepMerge({
        val ongoing = for ((token, startTime) <- snapshot.ongoing) yield {
          Json.obj("uuid" -> token.uuid.asJson, "status" -> "running".asJson, "startTime" -> startTime.asJson)
        }
        val finished = snapshot.cache.storage.map({
          case (token, cacheEntry) =>
            cacheEntry.entryType match {
              case ExecutionCacheEntryType.TokenEvaluationStats(start, end) =>
                Json.obj(
                  "uuid" -> token.uuid.asJson,
                  "status" -> "completed".asJson,
                  "startTime" -> start.asJson,
                  "completeTime" -> end.asJson
                )
              case ExecutionCacheEntryType.Injected =>
                Json.obj("uuid" -> token.uuid.asJson, "status" -> "injected".asJson)
              case ExecutionCacheEntryType.Cached =>
                Json.obj("uuid" -> token.uuid.asJson, "status" -> "cached".asJson)
            }
        })
        Json.obj(
          "targetToken" -> tokenToJson(snapshot.tokenToEvaluate),
          "topology" -> (for (tokens <- tokensByLayer) yield {
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
          }).asJson,
          "asOfTime" -> snapshot.asOfTime.asJson,
          "timeline" -> (ongoing ++ finished).asJson
        )
      })
    }
  }
}
