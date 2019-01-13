package com.roboxue.niffler.execution

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{ActorRefFactory, Props}
import com.roboxue.niffler.execution.actor.EvaluationCommander
import com.roboxue.niffler.{ExecutionStateLike, ExecutionStateTracker, Logic, Token}

import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.duration.Duration

/**
  * @author robert.xue
  * @since 7/15/18
  */
case class AsyncExecution[T] private (logic: Logic,
                                      initialState: ExecutionStateLike,
                                      token: Token[T],
                                      executionId: Int,
                                      stateTracker: Option[ExecutionStateTracker] = None) {
  private val resultPromise: Promise[ExecutionResult[T]] = Promise()
  private var cancelled: Boolean = false

  private[niffler] def failure(ex: Throwable): Unit = {
    resultPromise.failure(ex)
  }

  private[niffler] def success(result: ExecutionResult[T]): Unit = {
    resultPromise.success(result)
  }

  def cancel(): Unit = {
    cancelled = true
  }

  def withAkka(implicit context: ActorRefFactory): AsyncExecution[T] = {
    context.actorOf(Props(new EvaluationCommander(this)), s"evaluation-$executionId")
    this
  }

  def awaitValue(duration: Duration): T = {
    await(duration).value
  }

  def await(duration: Duration): ExecutionResult[T] = {
    val r = Await.result(resultPromise.future, duration)
    for (st <- stateTracker) {
      st.setExecutionState(r.state)
    }
    r
  }

  def future: Future[ExecutionResult[T]] = {
    resultPromise.future
  }
}

object AsyncExecution {
  val executionId: AtomicInteger = new AtomicInteger(0)
}
