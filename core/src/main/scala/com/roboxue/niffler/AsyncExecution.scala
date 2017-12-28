package com.roboxue.niffler

import java.time.Clock
import java.util.concurrent.{TimeUnit, TimeoutException}

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.util.Timeout
import com.roboxue.niffler.execution._

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future, Promise}

/**
  * @author rxue
  * @since 12/15/17.
  */
object AsyncExecution {
  def apply[T](logic: Logic, token: Token[T], cache: ExecutionCache): AsyncExecution[T] = {
    new AsyncExecution[T](logic, cache, token, Niffler.getActorSystem).trigger()
  }
}

/**
  * Use companion object to create an instance. Basically a rich wrapper around a promise
  * This class wraps all immutable information about a round of execution
  *
  * @param logic the logic being evaluated
  * @param initialCache the cache being reused
  * @param forToken the token being invoked
  * @param system the akka actor system used to create actors
  * @param clock the source of time, useful when testing
  */
class AsyncExecution[T] private (logic: Logic,
                                 initialCache: ExecutionCache,
                                 forToken: Token[T],
                                 system: ActorSystem,
                                 clock: Clock = Clock.systemUTC()) {
  private val promise: Promise[ExecutionResult[T]] = Promise()
  private lazy val executionActor: ActorRef =
    system.actorOf(ExecutionActor.props(promise, logic, initialCache, forToken, clock))

  def future: Future[ExecutionResult[T]] = promise.future

  /**
    * Wrapper around Await(promise.future, timeout), yield a more friendly [[NifflerTimeoutException]] on timeout
    *
    * @param timeout either [[Duration.Inf]] or a [[FiniteDuration]]
    * @return execution result if successfully executed
    * @throws NifflerTimeoutException    if timeout
    * @throws NifflerEvaluationException if runtime exception encountered
    */
  def await(timeout: Duration): ExecutionResult[T] = {
    try {
      Await.result(promise.future, timeout)
    } catch {
      case _: TimeoutException if timeout.isFinite() =>
        import akka.pattern._
        val cancelTimeout = Duration(3, TimeUnit.SECONDS)
        val snapshot = Await.result(
          (executionActor ? ExecutionActor.Cancel)(Timeout(cancelTimeout)).mapTo[ExecutionSnapshot],
          cancelTimeout
        )
        throw NifflerTimeoutException(snapshot, timeout.asInstanceOf[FiniteDuration])
      case ex: Throwable =>
        throw ex
    } finally {
      executionActor ! PoisonPill
    }
  }

  /**
    * Trigger execution by sending a [[ExecutionActor.Invoke]] message to a new [[ExecutionActor]]
    * @return
    */
  private[niffler] def trigger(): AsyncExecution[T] = {
    val missingImpl = logic.checkMissingImpl(initialCache, forToken)
    if (missingImpl.nonEmpty) {
      val now = clock.millis()
      promise.tryFailure(
        NifflerInvocationException(ExecutionSnapshot(logic, forToken, initialCache, Map.empty, now, now), missingImpl)
      )
    } else {
      executionActor ! ExecutionActor.Invoke
      promise.future.onComplete(_ => executionActor ! PoisonPill)(ExecutionContext.global)
    }
    this
  }
}
