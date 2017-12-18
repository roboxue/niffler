package com.roboxue.niffler

/**
  * @author rxue
  * @since 12/18/17.
  */
sealed case class SyncExecution[T](result: T, cache: ExecutionCache)
