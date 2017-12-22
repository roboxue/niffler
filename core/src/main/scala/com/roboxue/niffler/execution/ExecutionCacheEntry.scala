package com.roboxue.niffler.execution

/**
  * @author rxue
  * @since 12/19/17.
  */
case class ExecutionCacheEntry[T](result: T, stats: TokenEvaluationStats, ttl: Option[Long])
