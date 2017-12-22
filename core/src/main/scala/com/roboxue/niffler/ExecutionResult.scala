package com.roboxue.niffler

import com.roboxue.niffler.execution.ExecutionSnapshot

/**
  * @author rxue
  * @since 12/18/17.
  */
sealed case class ExecutionResult[T](result: T, snapshot: ExecutionSnapshot, cacheAfterExecution: ExecutionCache)
