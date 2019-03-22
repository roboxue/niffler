package com.roboxue.niffler

import com.roboxue.niffler.execution.AsyncExecution

import scala.collection.JavaConverters._

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

  def asyncRun[T](token: Token[T], extraFlow: java.lang.Iterable[DataFlow[_]], sc: ExecutionStateTracker): AsyncExecution[T] = {
    new Logic(dataFlows).asyncRun(token, extraFlow.asScala)(sc)
  }

  def ++(another: Niffler): Niffler = new Niffler {
    override def dataFlows: Iterable[DataFlow[_]] = {
      self.dataFlows ++ another.dataFlows
    }
  }
}
