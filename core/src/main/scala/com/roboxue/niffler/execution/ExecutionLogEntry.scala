package com.roboxue.niffler.execution

import com.roboxue.niffler.Token

/**
  * @author robert.xue
  * @since 7/15/18
  */
sealed trait ExecutionLogEntry {
  val nanoTime: Long = System.nanoTime()
}

case class LogStarted(executionId: Int, timestamp: Long = System.currentTimeMillis()) extends ExecutionLogEntry
case class TokenAnalyzed(token: Token[_], unmet: Set[Token[_]], met: Set[Token[_]]) extends ExecutionLogEntry
case class TokenBacklogged(token: Token[_], unmet: Set[Token[_]]) extends ExecutionLogEntry
case class TokenStartedEvaluation(token: Token[_]) extends ExecutionLogEntry
case class TokenEndedEvaluation(token: Token[_]) extends ExecutionLogEntry
case class TokenFailedEvaluation(token: Token[_], ex: Throwable) extends ExecutionLogEntry
case class TokenCancelledEvaluation(token: Token[_], canceledBecause: Option[Token[_]]) extends ExecutionLogEntry
case class LogEnded(timestamp: Long = System.currentTimeMillis()) extends ExecutionLogEntry
