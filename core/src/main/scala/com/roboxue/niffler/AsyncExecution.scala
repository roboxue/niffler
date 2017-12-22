package com.roboxue.niffler

import java.util.concurrent.{TimeUnit, TimeoutException}

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.util.Timeout

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future, Promise}

/**
  * @author rxue
  * @since 12/15/17.
  */
object AsyncExecution {
  private var actorSystem: Option[ActorSystem] = None

  def setActorSystem(system: ActorSystem): Unit = {
    actorSystem = Some(system)
  }

  private def getActorSystem: ActorSystem = {
    if (actorSystem.isEmpty) {
      actorSystem = Some(ActorSystem("niffler"))
    }
    actorSystem.get
  }

  def apply[T](logic: Logic, token: Token[T], cache: ExecutionCache): AsyncExecution[T] = {
    new AsyncExecution[T](logic, cache, token, getActorSystem).trigger()
  }
}

/**
  * Use companion object to create an instance
  * This class wraps all immutable information about
  */
class AsyncExecution[T] private (logic: Logic, initialCache: ExecutionCache, forToken: Token[T], system: ActorSystem) {
  private val promise: Promise[ExecutionResult[T]] = Promise()
  private lazy val executionActor: ActorRef =
    system.actorOf(ExecutionActor.props(promise, logic, initialCache, forToken))

  def future: Future[ExecutionResult[T]] = promise.future

  /**
    * Wrapper around Await(promise.future, timeout), yield a more friendly [[NifflerTimeoutException]] on timeout
    * @param timeout either [[Duration.Inf]] or a [[FiniteDuration]]
    * @return execution result if successfully executed
    * @throws NifflerTimeoutException if timeout
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

  private[niffler] def trigger(): AsyncExecution[T] = {
    executionActor ! ExecutionActor.Invoke
    promise.future.onComplete(_ => executionActor ! PoisonPill)(ExecutionContext.global)
    this
  }
}
