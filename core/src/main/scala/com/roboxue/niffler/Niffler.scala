package com.roboxue.niffler
import com.roboxue.niffler.execution.AsyncExecution

/**
  * @author robert.xue
  * @since 7/15/18
  */
object Niffler {}

trait Niffler {
  self =>
  def dataFlows: Iterable[DataFlow[_]]

  def asyncRun[T](token: Token[T], extraFlow: Iterable[DataFlow[_]] = Iterable.empty)(
    implicit sc: ExecutionStateTracker = new ExecutionStateTracker
  ): AsyncExecution[T] = {
    new Logic(dataFlows).asyncRun(token, extraFlow)(sc)
  }

  def ++(another: Niffler): Niffler = new Niffler {
    override def dataFlows: Iterable[DataFlow[_]] = {
      self.dataFlows ++ another.dataFlows
    }
  }
}
