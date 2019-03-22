package com.roboxue.niffler.execution

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

import com.roboxue.niffler.Token

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

/**
  * @author robert.xue
  * @since 7/15/18
  */
case class ExecutionLog(executionId: Int, logLines: Iterable[ExecutionLogEntry]) {
  require(logLines.nonEmpty)
  require(logLines.head.isInstanceOf[LogStarted])
  private val started = logLines.head.asInstanceOf[LogStarted]

  def printWaterfall(logger: String => Unit): Unit = {
    logLines.foreach({
      case l: LogStarted =>
        logger(s"${l.nanoTime} LogStarted for execution $executionId")
      case l: TokenAnalyzed =>
        logger(s"${l.nanoTime} TokenAnalyzed ${l.token.codeName}, unmet: ${l.unmet.size}, met: ${l.met.size}")
      case l: TokenBacklogged =>
        logger(s"${l.nanoTime} TokenBacklogged ${l.token.codeName}, unmet: ${l.unmet.size}")
      case l: TokenRevisited =>
        logger(s"${l.nanoTime} TokenRevisited ${l.token.codeName}, unmet: ${l.unmet.size}")
      case l: TokenStartedEvaluation =>
        logger(s"${l.nanoTime} TokenStartedEvaluation ${l.token.codeName}")
      case l: TokenEndedEvaluation =>
        logger(s"${l.nanoTime} TokenEndedEvaluation ${l.token.codeName}")
      case l: TokenFailedEvaluation =>
        logger(s"${l.nanoTime} TokenFailedEvaluation ${l.token.codeName}")
      case l: TokenCancelledEvaluation =>
        logger(
          s"${l.nanoTime} TokenCancelledEvaluation ${l.token.codeName} because of ${l.canceledBecause.map(_.codeName).getOrElse("external reason")}"
        )
      case l: LogEnded =>
        logger(s"${l.nanoTime} LogEnded for execution $executionId")
    })
  }

  def printFlowChartJava(logger: Consumer[String]): Unit = {
    printFlowChart(str => {
      logger.accept(str)
    })
  }

  /**
    * Syntax from http://plantuml.com/sequence-diagram
    */
  def printFlowChart(logger: String => Unit): Unit = {
    val cache: mutable.Map[String, Long] = mutable.Map.empty
    val seenTokenUuid: mutable.Set[String] = mutable.Set.empty

    def printTokenDefIfNeeded(token: Token[_]): Unit = {
      if (!seenTokenUuid.contains(token.uuid)) {
        seenTokenUuid += token.uuid
        logger(s"""control "${token.name}: ${token.typeDescription}" as ${token.uuid.replaceAll("-", "")}""")
      }
    }

    logLines.foreach({
      case l: LogStarted =>
        logger(s"@startuml")
        logger(s"""participant "Execution $executionId" as exe""")
        logger(s"... Execution Started ~~${DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(l.timestamp))}~~ ...")

      case l: TokenAnalyzed =>
        printTokenDefIfNeeded(l.token)
        (l.met ++ l.unmet).foreach(printTokenDefIfNeeded)
        logger(s"exe --> ${l.token.uuid.replaceAll("-", "")}: Analyzed (${l.met.size}/${l.met.size + l.unmet.size} dep met)")
      case l: TokenBacklogged =>
        l.unmet.foreach(t => {
          logger(s"${t.uuid.replaceAll("-", "")} x-> ${l.token.uuid.replaceAll("-", "")}: Blocked by")
        })
      case l: TokenStartedEvaluation =>
        logger(s"activate ${l.token.uuid.replaceAll("-", "")}")
        cache(l.token.uuid) = l.nanoTime
      case l: TokenEndedEvaluation =>
        logger(s"deactivate ${l.token.uuid.replaceAll("-", "")}")
        logger(
          s"""note over ${l.token.uuid.replaceAll("-", "")}
             |  Finished (${FiniteDuration(l.nanoTime - cache(l.token.uuid), TimeUnit.NANOSECONDS)})
             |end note
             |""".stripMargin)
      case l: TokenRevisited =>
        logger(s"${l.token.uuid.replaceAll("-", "")} <-o ${l.blockerRemoved.uuid.replaceAll("-", "")}: Unblocked (${l.met.size}/${l.met.size + l.unmet.size} dep met)")
      case l: TokenFailedEvaluation =>
        logger(s"exe --> ${l.token.uuid.replaceAll("-", "")}: Failed because ${l.ex.getMessage} (${FiniteDuration(l.nanoTime - cache(l.token.uuid), TimeUnit.NANOSECONDS)})")
        logger(s"deactivate ${l.token.uuid.replaceAll("-", "")}")
      case l: TokenCancelledEvaluation =>
        val cause = if (l.canceledBecause.isEmpty) "exe" else l.canceledBecause.get.uuid.replaceAll("-", "")
        logger(s"$cause --> ${l.token.uuid.replaceAll("-", "")}: Cancelled at ${l.nanoTime} (${FiniteDuration(l.nanoTime - cache(l.token.uuid), TimeUnit.NANOSECONDS)})")
        logger(s"deactivate ${l.token.uuid.replaceAll("-", "")}")
      case l: LogEnded =>
        logger(s"... Execution Finished (${l.timestamp - started.timestamp} ms) ~~${DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(l.timestamp))}~~ ...")
        logger(s"@enduml")
    })
  }
}
