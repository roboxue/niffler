package com.roboxue.niffler.execution
import java.time.{Instant, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.logging.{Level, Logger}

import com.roboxue.niffler.Token

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

/**
  * @author robert.xue
  * @since 2019-04-02
  */
class FlowChartExecutionLogger(logger: Logger, level: Level = Level.INFO) extends ExecutionLogger {
  val cache: mutable.Map[String, Long] = mutable.Map.empty
  val seenTokenUuid: mutable.Set[String] = mutable.Set.empty
  var started: Option[LogStarted] = None

  def printTokenDefIfNeeded(token: Token[_]): Unit = {
    if (!seenTokenUuid.contains(token.uuid)) {
      seenTokenUuid += token.uuid
      logger.log(level, s"""control "${token.codeName}" as ${token.uuid}""")
    }
  }

  override def onLogStarted(event: LogStarted): Unit = {
    logger.log(level, s"@startuml")
    logger.log(level, s"""participant "Execution ${event.executionId}" as exe""")
    logger.log(level, s"... Execution Started ~~${DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(event.timestamp))}~~ ...")
    started = Some(event)
  }

  override def onTokenAnalyzed(event: TokenAnalyzed): Unit = {
    printTokenDefIfNeeded(event.token)
    (event.met ++ event.unmet).foreach(printTokenDefIfNeeded)
    logger.log(level, s"exe --> ${event.token.uuid}: Analyzed (${event.met.size}/${event.met.size + event.unmet.size} dep met)")
  }

  override def onTokenBacklogged(event: TokenBacklogged): Unit = {
    event.unmet.foreach(t => {
      logger.log(level, s"${t.uuid} x-> ${event.token.uuid}: Blocked by")
    })

  }

  override def onTokenStartedEvaluation(event: TokenStartedEvaluation): Unit = {
    logger.log(level, s"activate ${event.token.uuid}")
    cache(event.token.uuid) = event.nanoTime
  }

  override def onTokenEndedEvaluation(event: TokenEndedEvaluation): Unit = {
    logger.log(level, s"deactivate ${event.token.uuid}")
    logger.log(level,
      s"""note over ${event.token.uuid}
         |  Finished (${FiniteDuration(event.nanoTime - cache(event.token.uuid), TimeUnit.NANOSECONDS)})
         |end note
         |""".stripMargin)
  }

  override def onTokenRevisited(event: TokenRevisited): Unit = {
    logger.log(level, s"${event.token.uuid} <-o ${event.blockerRemoved.uuid}: Unblocked (${event.met.size}/${event.met.size + event.unmet.size} dep met)")
  }

  override def onTokenFailedEvaluation(event: TokenFailedEvaluation): Unit = {
    logger.log(level, s"exe --> ${event.token.uuid}: Failed because ${event.ex.getMessage} (${FiniteDuration(event.nanoTime - cache(event.token.uuid), TimeUnit.NANOSECONDS)})")
    logger.log(level, s"deactivate ${event.token.uuid}")
  }

  override def onTokenCancelledEvaluation(event: TokenCancelledEvaluation): Unit = {
    val cause = if (event.canceledBecause.isEmpty) "exe" else event.canceledBecause.get.uuid
    logger.log(level, s"$cause --> ${event.token.uuid}: Cancelled at ${event.nanoTime} (${FiniteDuration(event.nanoTime - cache(event.token.uuid), TimeUnit.NANOSECONDS)})")
    logger.log(level, s"deactivate ${event.token.uuid}")
  }

  override def onLogEnded(event: LogEnded): Unit = {
    started match {
      case Some(s) =>
        logger.log(level, s"... Execution Finished (${event.timestamp - s.timestamp} ms) ~~${DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(event.timestamp))}~~ ...")
      case None =>
        logger.log(level, s"... Execution Finished ~~${DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(event.timestamp))}~~ ...")
    }
    logger.log(level, s"@enduml")
  }
}
