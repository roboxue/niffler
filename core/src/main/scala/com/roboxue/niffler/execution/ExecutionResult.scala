package com.roboxue.niffler.execution

import com.roboxue.niffler.ExecutionState

/**
  * @author robert.xue
  * @since 7/15/18
  */
case class ExecutionResult[T](value: T, state: ExecutionState, executionLog: ExecutionLog, executionId: Int)
