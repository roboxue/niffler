package com.roboxue.niffler.execution
import java.util.logging.{Level, Logger}

/**
  * @author robert.xue
  * @since 220
  */
class WaterfallExecutionLogger(logger: Logger, level: Level = Level.INFO) extends ExecutionLogger {
  override def onLogStarted(event: LogStarted): Unit = {
    logger.log(level, s"${event.timestamp} [${event.executionId}][LogStarted]", None)
  }

  override def onTokenAnalyzed(event: TokenAnalyzed): Unit = {
    logger.log(level, s"${event.timestamp} [${event.executionId}][TokenAnalyzed] ${event.token.codeName}", None)
  }

  override def onTokenBacklogged(event: TokenBacklogged): Unit = {
    logger.log(level, s"${event.timestamp} [${event.executionId}][TokenBacklogged] ${event.token.codeName} because of ${event.unmet.size} blockers", None)
  }

  override def onTokenStartedEvaluation(event: TokenStartedEvaluation): Unit = {
    logger.log(level, s"${event.timestamp} [${event.executionId}][TokenStartedEvaluation] ${event.token.codeName}", None)
  }

  override def onTokenEndedEvaluation(event: TokenEndedEvaluation): Unit = {
    logger.log(level, s"${event.timestamp} [${event.executionId}][TokenEndedEvaluation] ${event.token.codeName}", None)
  }

  override def onTokenRevisited(event: TokenRevisited): Unit = {
    logger.log(level, s"${event.timestamp} [${event.executionId}][TokenRevisited] ${event.token.codeName} since blocker ${event.blockerRemoved.codeName} has been resolved", None)
  }

  override def onTokenFailedEvaluation(event: TokenFailedEvaluation): Unit = {
    logger.log(level, s"${event.timestamp} [${event.executionId}][TokenFailedEvaluation] ${event.token.codeName}", Some(event.ex))
  }

  override def onTokenCancelledEvaluation(event: TokenCancelledEvaluation): Unit = {
    event.canceledBecause match {
      case Some(cause) =>
        logger.log(level, s"${event.timestamp} [${event.executionId}][TokenCancelledEvaluation] ${event.token.codeName} due to issues in blocker ${cause.codeName}", None)
      case None =>
        logger.log(level, s"${event.timestamp} [${event.executionId}][TokenCancelledEvaluation] ${event.token.codeName}", None)
    }
  }

  override def onLogEnded(event: LogEnded): Unit = {
    logger.log(level, s"${event.timestamp} [${event.executionId}][LogEnded]", None)
  }
}
