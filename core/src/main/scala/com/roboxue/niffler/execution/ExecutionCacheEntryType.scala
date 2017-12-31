package com.roboxue.niffler.execution

/**
  * @author rxue
  * @since 12/19/17.
  */
sealed trait ExecutionCacheEntryType

object ExecutionCacheEntryType {
  case class TokenEvaluationStats(startTime: Long, completeTime: Long) extends ExecutionCacheEntryType {
    override def toString: String = s"$startTime -> $completeTime"
  }
  case object Injected extends ExecutionCacheEntryType
  case object Inherited extends ExecutionCacheEntryType
}
