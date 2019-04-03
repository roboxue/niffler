package com.roboxue.niffler.execution

/**
  * @author robert.xue
  * @since 7/15/18
  */
case class ExecutionLog(executionId: Int, logLines: Iterable[ExecutionLogEntry]) {
  require(logLines.nonEmpty)
  require(logLines.head.isInstanceOf[LogStarted])
}
