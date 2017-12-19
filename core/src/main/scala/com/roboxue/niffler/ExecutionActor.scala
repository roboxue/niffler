package com.roboxue.niffler

import java.time.Clock

import akka.actor.{Actor, Props}

import scala.collection.mutable
import scala.concurrent.Promise
import scala.util.{Failure, Success}

/**
  * @author rxue
  * @since 12/18/17.
  */
class ExecutionActor[T](promise: Promise[ExecutionResult[T]],
                        logic: Logic,
                        initialCache: ExecutionCache,
                        forKey: Key[T],
                        clock: Clock = Clock.systemUTC())
    extends Actor {
  val cacheDelta: ExecutionCache = ExecutionCache.empty
  val executionStartTime: mutable.Map[Key[_], Long] = mutable.Map.empty
  var invokeTime: Long = 0L
  var cancelled: Boolean = false
  val unmetDependencies: Set[Key[_]] = {
    logic.getUnmetDependencies(forKey, executionCache)
  }

  def executionCache: ExecutionCache = {
    initialCache merge cacheDelta
  }

  def triggerEval(key: Key[_]): Unit = {
    executionStartTime(key) = clock.millis()
    context.actorOf(
      Props(
        new KeyEvaluationActor[key.R0](
          key.asInstanceOf[Key[key.R0]],
          logic.implForKey(key).asInstanceOf[ImplementationDetails[key.R0]]
        )
      )
    ) ! KeyEvaluationActor.Evaluate(executionCache)
  }

  override def receive: Receive = {
    case ExecutionActor.Cancel =>
      cancelled = true
      val ec = executionCache
      sender() ! ExecutionSnapshot(logic, forKey, ec, executionStartTime.toMap -- ec.keys, invokeTime)
    case ExecutionActor.Invoke =>
      if (!cancelled) {
        invokeTime = clock.millis()
        val ec = executionCache
        if (unmetDependencies.isEmpty) {
          promise.trySuccess(ExecutionResult(ec(forKey), logic, ec))
        } else {
          for (k <- unmetDependencies if logic.allDependenciesMet(k, ec)) {
            triggerEval(k)
          }
        }
      }
    case KeyEvaluationActor.EvaluateComplete(key, tryResult) =>
      if (!cancelled) {
        val stats = KeyEvaluationStats(executionStartTime(key), clock.millis())
        tryResult match {
          case Failure(ex) =>
            promise.tryFailure(NifflerEvaluationException(logic, forKey, key, stats, ex))
          case Success(result) =>
            // TODO: this is where cache policy applies
            cacheDelta.store(key, result, stats)
            if (key == forKey) {
              promise.trySuccess(ExecutionResult(result.asInstanceOf[T], logic, executionCache))
            } else {
              val parents = logic.getParents(key)
              for (k <- parents.intersect(unmetDependencies) if logic.allDependenciesMet(k, executionCache)) {
                triggerEval(k)
              }
            }
        }
      }
  }
}

object ExecutionActor {

  case object Invoke
  case object Cancel

}
