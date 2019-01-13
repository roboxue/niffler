package com.roboxue.niffler
import com.roboxue.niffler.execution.AsyncExecution

/**
  * @author robert.xue
  * @since 7/15/18
  */
object Niffler {}

trait Niffler {
  val logic: Logic

  def asyncRun[T](token: Token[T], extraFlow: Iterable[DataFlow[_]] = Iterable.empty)(
    implicit sc: ExecutionStateTracker = new ExecutionStateTracker
  ): AsyncExecution[T] = {
    if (extraFlow.isEmpty) {
      AsyncExecution(logic, sc.getExecutionState, token, AsyncExecution.executionId.incrementAndGet(), Some(sc))
    } else {
      AsyncExecution(
        logic.diverge(extraFlow),
        sc.getExecutionState,
        token,
        AsyncExecution.executionId.incrementAndGet(),
        Some(sc)
      )
    }
  }
}
