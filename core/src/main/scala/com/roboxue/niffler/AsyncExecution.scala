package com.roboxue.niffler

import akka.actor.{ActorSystem, PoisonPill, Props}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Promise}

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

  def apply[T](logic: Logic, key: Key[T], cache: ExecutionCache): AsyncExecution[T] = {
    new AsyncExecution[T](logic, cache, key, getActorSystem).trigger()
  }
}

class AsyncExecution[T] private (logic: Logic, initialCache: ExecutionCache, forKey: Key[T], system: ActorSystem) {
  val promise: Promise[ExecutionResult[T]] = Promise()

  private[niffler] def await: ExecutionResult[T] = {
    Await.result(promise.future, Duration.Inf)
  }

  private[niffler] def trigger(): AsyncExecution[T] = {
    val executionActor = system.actorOf(Props(new ExecutionActor[T](promise, logic, initialCache, forKey)))
    executionActor ! ExecutionActor.Invoke
    promise.future.onComplete(_ => executionActor ! PoisonPill)(ExecutionContext.global)
    this
  }
}
