package com.roboxue.niffler

/**
  * @author rxue
  * @since 12/18/17.
  */
sealed case class ExecutionResult[T](result: T, logic: Logic, cache: ExecutionCache)
