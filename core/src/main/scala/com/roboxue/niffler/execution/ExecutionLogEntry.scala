package com.roboxue.niffler.execution

import com.roboxue.niffler.Token

/**
  * @author robert.xue
  * @since 7/15/18
  */
sealed trait ExecutionLogEntry {
  val nanoTime: Long = System.nanoTime()
  val timestamp: Long = System.currentTimeMillis()
  val executionId: Int
}

case class LogStarted(executionId: Int) extends ExecutionLogEntry
case class TokenAnalyzed(executionId: Int, token: Token[_], unmet: Set[Token[_]], met: Set[Token[_]]) extends ExecutionLogEntry
case class TokenBacklogged(executionId: Int, token: Token[_], unmet: Set[Token[_]]) extends ExecutionLogEntry
case class TokenStartedEvaluation(executionId: Int, token: Token[_]) extends ExecutionLogEntry
case class TokenEndedEvaluation(executionId: Int, token: Token[_]) extends ExecutionLogEntry
case class TokenRevisited(executionId: Int, token: Token[_], blockerRemoved: Token[_], unmet: Set[Token[_]], met: Set[Token[_]]) extends ExecutionLogEntry
case class TokenFailedEvaluation(executionId: Int, token: Token[_], ex: Throwable) extends ExecutionLogEntry
case class TokenCancelledEvaluation(executionId: Int, token: Token[_], canceledBecause: Option[Token[_]]) extends ExecutionLogEntry
case class LogEnded(executionId: Int) extends ExecutionLogEntry
