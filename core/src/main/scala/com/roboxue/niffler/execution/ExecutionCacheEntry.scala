package com.roboxue.niffler.execution

import java.time.Clock

/**
  * @author rxue
  * @since 12/19/17.
  */
case class ExecutionCacheEntry[T](result: T, stats: TokenEvaluationStats, ttl: Option[Long])

object ExecutionCacheEntry {
  def apply[T](result: T, clock: Clock = Clock.systemUTC()): ExecutionCacheEntry[T] = {
    val now = clock.millis()
    new ExecutionCacheEntry(result, TokenEvaluationStats(now, now), None)
  }
}
