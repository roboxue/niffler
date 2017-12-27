package com.roboxue.niffler.execution

import java.time.Clock

import akka.actor.{Actor, Props}
import com.roboxue.niffler.{execution, _}

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
                        forToken: Token[T],
                        clock: Clock)
    extends Actor {
  val mutableCache: MutableExecutionCache = initialCache.mutableFork
  val executionStartTime: mutable.Map[Token[_], Long] = mutable.Map.empty
  var invokeTime: Long = 0L
  var cancelled: Boolean = false
  var unmetDependencies: Set[Token[_]] = Set.empty

  def triggerEval(token: Token[_]): Unit = {
    executionStartTime(token) = clock.millis()
    val typedToken: Token[token.T0] = token.asInstanceOf[Token[token.T0]]
    context.actorOf(TokenEvaluationActor.props(typedToken, logic.implForToken(typedToken))) ! TokenEvaluationActor
      .Evaluate(mutableCache.fork)
  }

  override def receive: Receive = {
    case ExecutionActor.Cancel =>
      cancelled = true
      sender() ! getExecutionSnapshot(clock.millis())
    case ExecutionActor.Invoke =>
      if (!cancelled) {
        invokeTime = clock.millis()
        mutableCache.invalidateTtlCache(invokeTime)
        val ec = mutableCache.omit(Set(forToken))
        // Check TTL during invoke, invalidate
        unmetDependencies = logic.getUnmetDependencies(forToken, ec)
        if (unmetDependencies.isEmpty) {
          triggerEval(forToken)
        } else {
          for (k <- unmetDependencies if logic.allDependenciesMet(k, ec)) {
            triggerEval(k)
          }
        }
      }
    case TokenEvaluationActor.EvaluateComplete(token, tryResult) =>
      if (!cancelled) {
        tryResult match {
          case Failure(ex) =>
            val now = clock.millis()
            val stats = TokenEvaluationStats(executionStartTime(token), now)
            promise.tryFailure(NifflerEvaluationException(getExecutionSnapshot(now), token, stats, ex))
          case Success(result) =>
            val now = clock.millis()
            val stats = TokenEvaluationStats(executionStartTime(token), now)
            logic.cachingPolicy(token) match {
              case CachingPolicy.WithinExecution | CachingPolicy.Forever =>
                mutableCache.store(token, result, stats, None)
              case CachingPolicy.Timed(ttl) =>
                mutableCache.store(token, result, stats, Some(ttl.length))
            }
            if (token == forToken) {
              promise.trySuccess(
                ExecutionResult(
                  result.asInstanceOf[T],
                  ExecutionSnapshot(logic, forToken, mutableCache.fork, Map.empty, invokeTime, now),
                  cacheAfterExecution(now)
                )
              )
            } else {
              val ec = mutableCache.fork
              for (k <- logic.getParents(token).intersect(unmetDependencies) if logic.allDependenciesMet(k, ec)) {
                triggerEval(k)
              }
            }
        }
      }
  }

  def getExecutionSnapshot(now: Long): ExecutionSnapshot = {
    val ec = mutableCache.fork
    execution.ExecutionSnapshot(logic, forToken, ec, executionStartTime.toMap -- ec.tokens, invokeTime, now)
  }

  def cacheAfterExecution(now: Long): ExecutionCache = {
    val tokensCachedOnlyWithinExecution =
      logic.tokensInvolved.filter(k => logic.cachingPolicy(k) == CachingPolicy.WithinExecution)
    mutableCache.invalidateTtlCache(now)
    mutableCache.omit(tokensCachedOnlyWithinExecution)
  }
}

object ExecutionActor {

  case object Invoke

  case object Cancel

  def props[T](promise: Promise[ExecutionResult[T]],
               logic: Logic,
               initialCache: ExecutionCache,
               forToken: Token[T],
               clock: Clock): Props = {
    Props(new ExecutionActor[T](promise, logic, initialCache, forToken, clock))
  }

}
