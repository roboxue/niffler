package com.roboxue.niffler

import java.time.Clock
import java.util.concurrent.{TimeUnit, TimeoutException}

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.util.Timeout
import com.roboxue.niffler.execution._
import monix.execution.atomic.AtomicInt

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Await, Promise}
import scala.util.{Failure, Success}

/**
  * @author rxue
  * @since 12/15/17.
  */
object AsyncExecution {
  private val executionId: AtomicInt = AtomicInt(0)

  def apply[T](logic: Logic, token: Token[T], cache: ExecutionCache): AsyncExecution[T] = {
    new AsyncExecution[T](logic, cache, token, Niffler.getActorSystem)
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
  * @param executionId unique id for this execution
  */
case class AsyncExecution[T] private (logic: Logic,
                                      initialCache: ExecutionCache,
                                      forToken: Token[T],
                                      system: ActorSystem,
                                      clock: Clock = Clock.systemUTC(),
                                      executionId: Int = AsyncExecution.executionId.incrementAndGet()) {
  val promise: Promise[ExecutionResult[T]] = Promise()
  private var finalizedSnapshot: Option[ExecutionSnapshot] = None
  private lazy val executionActor: ActorRef =
    system.actorOf(ExecutionActor.props(promise, logic, initialCache, forToken, clock))

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
        executionActor ! ExecutionActor.Cancel
        val ex = NifflerTimeoutException(getExecutionSnapshot, timeout.asInstanceOf[FiniteDuration])
        promise.tryFailure(ex)
        throw ex
      case ex: Throwable =>
        throw ex
    } finally {
      executionActor ! PoisonPill
    }
  }

  def requestCancellation(): Unit = {
    executionActor ! ExecutionActor.Cancel
  }

  def getExecutionSnapshot: ExecutionSnapshot = {
    finalizedSnapshot.getOrElse({
      import akka.pattern._
      val cancelTimeout = Duration(3, TimeUnit.SECONDS)
      Await.result(
        (executionActor ? ExecutionActor.GetSnapshot)(Timeout(cancelTimeout)).mapTo[ExecutionSnapshot],
        cancelTimeout
      )
    })
  }

  Niffler.registerNewExecution(this)
  promise.future.onComplete(result => {
    finalizedSnapshot = result match {
      case Success(r) =>
        Some(r.snapshot)
      case Failure(ex: NifflerExceptionBase) =>
        Some(ex.snapshot)
      case _ =>
        None // should never happen
    }
    executionActor ! PoisonPill
    Niffler.reportExecutionComplete(this)
  })(system.dispatcher)

  logic.checkMissingImpl(initialCache, forToken) match {
    case missingImpl if missingImpl.nonEmpty =>
      promise.tryFailure(
        NifflerInvocationException(
          ExecutionSnapshot(
            logic,
            forToken,
            initialCache,
            Map.empty,
            Some(clock.millis()),
            ExecutionStatus.Failed,
            clock.millis()
          ),
          missingImpl
        )
      )
    case _ =>
      executionActor ! ExecutionActor.Invoke
  }
}
