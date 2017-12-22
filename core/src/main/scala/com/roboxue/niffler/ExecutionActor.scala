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
  val mutableCache: MutableExecutionCache = initialCache.mutableFork
  val executionStartTime: mutable.Map[Key[_], Long] = mutable.Map.empty
  var invokeTime: Long = 0L
  var cancelled: Boolean = false
  var unmetDependencies: Set[Key[_]] = Set.empty

  def triggerEval(key: Key[_]): Unit = {
    executionStartTime(key) = clock.millis()
    context.actorOf(
      Props(
        new KeyEvaluationActor[key.R0](
          key.asInstanceOf[Key[key.R0]],
          logic.implForKey(key).asInstanceOf[ImplementationDetails[key.R0]]
        )
      )
    ) ! KeyEvaluationActor.Evaluate(mutableCache.fork)
  }

  override def receive: Receive = {
    case ExecutionActor.Cancel =>
      cancelled = true
      sender() ! getExecutionSnapshot
    case ExecutionActor.Invoke =>
      if (!cancelled) {
        invokeTime = clock.millis()
        val ec = mutableCache.fork
        // Check TTL during invoke, invalidate
        unmetDependencies = logic.getUnmetDependencies(forKey, ec)
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
            promise.tryFailure(NifflerEvaluationException(getExecutionSnapshot, key, stats, ex))
          case Success(result) =>
            logic.cachingPolicy(key) match {
              case CachingPolicy.WithinExecution | CachingPolicy.Forever =>
                mutableCache.store(key, result, stats, None)
              case CachingPolicy.Timed(ttl) =>
                mutableCache.store(key, result, stats, Some(ttl.length))
              case CachingPolicy.Never =>
              // pass
            }
            if (key == forKey) {
              val keysCachedOnlyWithinExecution =
                logic.keys.filter(k => logic.cachingPolicy(k) == CachingPolicy.WithinExecution)
              val cacheAfterExecution = mutableCache.omit(keysCachedOnlyWithinExecution)
              promise.trySuccess(ExecutionResult(result.asInstanceOf[T], logic, cacheAfterExecution))
            } else {
              val ec = mutableCache.fork
              for (k <- logic.getParents(key).intersect(unmetDependencies) if logic.allDependenciesMet(k, ec)) {
                triggerEval(k)
              }
            }
        }
      }
  }

  def getExecutionSnapshot: ExecutionSnapshot = {
    val ec = mutableCache.fork
    ExecutionSnapshot(logic, forKey, ec, executionStartTime.toMap -- ec.keys, invokeTime)
  }
}

object ExecutionActor {

  case object Invoke
  case object Cancel

}
