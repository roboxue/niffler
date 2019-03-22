package com.roboxue.niffler

import java.util.Optional

import com.roboxue.niffler.execution.{AsyncExecution, ExecutionLogger}

import scala.collection.JavaConverters._

/**
  * @author robert.xue
  * @since 7/15/18
  */
object Niffler {}

trait Niffler {
  self =>
  def dataFlows: Iterable[DataFlow[_]]

  def asyncRun[T](token: Token[T], extraFlow: Iterable[DataFlow[_]] = Iterable.empty, logger: Option[ExecutionLogger] = None)(
    implicit sc: ExecutionStateTracker = new ExecutionStateTracker
  ): AsyncExecution[T] = {
    new Logic(dataFlows).asyncRun(token, extraFlow, logger)(sc)
  }

  def asyncRun[T](token: Token[T], extraFlow: java.lang.Iterable[DataFlow[_]], sc: ExecutionStateTracker, logger: Optional[ExecutionLogger]): AsyncExecution[T] = {
    if (logger.isPresent) {
      new Logic(dataFlows).asyncRun(token, extraFlow.asScala, Some(logger.get()))(sc)
    } else {
      new Logic(dataFlows).asyncRun(token, extraFlow.asScala, None)(sc)
    }
  }

  def ++(another: Niffler): Niffler = new Niffler {
    override def dataFlows: Iterable[DataFlow[_]] = {
      self.dataFlows ++ another.dataFlows
    }
  }
}
