package com.roboxue.niffler.execution

import com.roboxue.niffler.ExecutionStateLike

/**
  * @author robert.xue
  * @since 7/15/18
  */
case class ExecutionResult[T](value: T, state: ExecutionStateLike, executionLog: ExecutionLog, executionId: Int)
