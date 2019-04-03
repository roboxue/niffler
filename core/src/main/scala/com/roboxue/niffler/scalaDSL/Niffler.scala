package com.roboxue.niffler.scalaDSL

import java.util.logging.{Level, Logger}

import com.roboxue.niffler._
import com.roboxue.niffler.execution.{AsyncExecution, ExecutionLogger}

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

  def ++(another: Niffler): Niffler = new Niffler {
    override def dataFlows: Iterable[DataFlow[_]] = {
      self.dataFlows ++ another.dataFlows
    }
  }

  def printGraph(logger: Logger, useCodeName: Boolean): Unit = {
    new Logic(dataFlows).printFlowChart(logger, Level.INFO, useCodeName)
  }

  def printGraph(logger: Logger, useCodeName: Boolean, extraFlow: Iterable[DataFlow[_]]): Unit = {
    new Logic(dataFlows ++ extraFlow).printFlowChart(logger, Level.INFO, useCodeName)
  }
}
