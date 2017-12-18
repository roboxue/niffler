package com.roboxue.niffler

import akka.actor.{Actor, Props}

import scala.concurrent.Promise
import scala.util.{Failure, Success}

/**
  * @author rxue
  * @since 12/18/17.
  */
class ExecutionActor[T](promise: Promise[SyncExecution[T]], logic: Logic, initialCache: ExecutionCache, forKey: Key[T])
    extends Actor {
  val cacheDelta: ExecutionCache = ExecutionCache.empty
  val unmetDependencies: Set[Key[_]] = {
    logic.getUnmetDependencies(forKey, executionCache)
  }

  def executionCache: ExecutionCache = {
    initialCache merge cacheDelta
  }

  override def receive: Receive = {
    case ExecutionActor.Invoke =>
      val ec = executionCache
      if (unmetDependencies.isEmpty) {
        promise.trySuccess(SyncExecution(ec(forKey), ec))
      } else {
        for (k <- unmetDependencies) {
          if (logic.dependencyMet(k, ec)) {
            context.actorOf(Props(new KeyEvaluationActor(logic.implForKey(k)))) ! KeyEvaluationActor.Evaluate(ec)
          }
        }
      }
    case KeyEvaluationActor.EvaluateComplete(key, tryResult) =>
      tryResult match {
        case Failure(ex) =>
          // TODO: proper reporting
          promise.tryFailure(ex)
        case Success(result) =>
          // TODO: this is where cache policy applies
          cacheDelta.store(key, result)
          if (key == forKey) {
            promise.trySuccess(SyncExecution(result.asInstanceOf[T], executionCache))
          } else {
            val parents = logic.getParents(key)
            for (k <- parents.intersect(unmetDependencies)) {
              if (logic.dependencyMet(k, executionCache)) {
                context.actorOf(Props(new KeyEvaluationActor(logic.implForKey(k)))) ! KeyEvaluationActor.Evaluate(
                  executionCache
                )
              }
            }
          }
      }
  }
}

object ExecutionActor {

  case object Invoke

}
