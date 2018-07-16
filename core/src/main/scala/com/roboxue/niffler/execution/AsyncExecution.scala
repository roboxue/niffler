package com.roboxue.niffler.execution

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{ActorContext, ActorRefFactory, Props}
import com.roboxue.niffler.execution.actor.EvaluationCommander
import com.roboxue.niffler.{ExecutionState, Logic, Token}

import scala.concurrent.Promise

/**
  * @author robert.xue
  * @since 7/15/18
  */
case class AsyncExecution[T] private (logic: Logic, initialState: ExecutionState, token: Token[T], executionId: Int) {
  val resultPromise: Promise[ExecutionResult[T]] = Promise()
  private var cancelled: Boolean = false

  def cancel(): Unit = {
    cancelled = true
  }

  def withAkka(implicit context: ActorRefFactory): Unit = {
    context.actorOf(Props(new EvaluationCommander(this)), s"evaluation-$executionId")
  }
}

object AsyncExecution {
  val executionId: AtomicInteger = new AtomicInteger(0)
}
