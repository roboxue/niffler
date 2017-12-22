package com.roboxue.niffler

/**
  * @author rxue
  * @since 12/19/17.
  */
case class ExecutionCacheEntry[T](result: T, stats: KeyEvaluationStats, ttl: Option[Long])
