package com.roboxue.niffler.execution

import java.time.Clock

import akka.actor.{FSM, Props}
import com.roboxue.niffler.execution.ExecutionCacheEntryType.TokenEvaluationStats
import com.roboxue.niffler.execution.ExecutionStatus._
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
    extends FSM[ExecutionStatus, Option[Long]] {
  val mutableCache: MutableExecutionCache = initialCache.mutableFork
  val executionStartTime: mutable.Map[Token[_], Long] = mutable.Map.empty
  var cancelled: Boolean = false
  var unmetPrerequisites: Set[Token[_]] = Set.empty

  startWith(Unstarted, None)

  when(Unstarted) {
    case Event(ExecutionActor.Invoke, _) =>
      val invokeTime = clock.millis()
      // Check TTL during invoke, invalidate
      mutableCache.invalidateTtlCache(invokeTime)
      // calculate the tokens that need to be recalculated during this round of invocation
      val ec = mutableCache.omit(Set(forToken))
      unmetPrerequisites = logic.getUnmetPrerequisites(forToken, ec)
      // trigger eval of prerequisites
      if (unmetPrerequisites.nonEmpty) {
        tryTriggerTokens(unmetPrerequisites, ec)
      } else {
        trigger(forToken)
      }
      goto(Running) using Some(invokeTime)
    case Event(ExecutionActor.GetSnapshot, _) =>
      sender() ! getExecutionSnapshot(clock.millis(), None)
      stay()
    case Event(ExecutionActor.Cancel, _) =>
      goto(Cancelled)
  }

  when(Running) {
    case Event(TokenEvaluationActor.EvaluateComplete(token, tryResult), invokeTime) =>
      tryResult match {
        case Failure(ex) =>
          announceFailure(token, ex, clock.millis(), invokeTime)
          goto(Failed)
        case Success(result) =>
          val now = clock.millis()
          val stats = TokenEvaluationStats(executionStartTime(token), now)
          // store result according to caching policy
          logic.cachingPolicy(token) match {
            case CachingPolicy.WithinExecution | CachingPolicy.Forever =>
              mutableCache.store(token, result, stats, None)
            case CachingPolicy.Timed(ttl) =>
              mutableCache.store(token, result, stats, Some(ttl.length))
          }
          // announce success or trigger next round of execution
          if (token == forToken) {
            announceSuccess(result.asInstanceOf[T], now, invokeTime)
            goto(Completed)
          } else {
            tryTriggerTokens(logic.getSuccessors(token).intersect(unmetPrerequisites), mutableCache.fork)
            stay()
          }
      }
    case Event(ExecutionActor.GetSnapshot, invokeTime) =>
      sender() ! getExecutionSnapshot(clock.millis(), invokeTime)
      stay()
    case Event(ExecutionActor.Cancel, _) =>
      goto(Cancelled)
  }

  when(Completed) {
    case Event(ExecutionActor.GetSnapshot, invokeTime) =>
      sender() ! getExecutionSnapshot(clock.millis(), invokeTime)
      stay()
  }

  when(Failed) {
    case Event(ExecutionActor.GetSnapshot, invokeTime) =>
      sender() ! getExecutionSnapshot(clock.millis(), invokeTime)
      stay()
  }

  when(Cancelled) {
    case Event(ExecutionActor.GetSnapshot, invokeTime) =>
      sender() ! getExecutionSnapshot(clock.millis(), invokeTime)
      stay()
  }

  whenUnhandled {
    case Event(TokenEvaluationActor.EvaluateComplete(_, _), _) =>
      //ignore this if not in Running state
      stay()
  }

  def announceFailure(cause: Token[_], ex: Throwable, now: Long, invokeTime: Option[Long]): Unit = {
    val stats = TokenEvaluationStats(executionStartTime(cause), now)
    promise.tryFailure(NifflerEvaluationException(getExecutionSnapshot(now, invokeTime), cause, stats, ex))
  }

  def announceSuccess(result: T, now: Long, invokeTime: Option[Long]): Unit = {
    promise.trySuccess(ExecutionResult(result, getExecutionSnapshot(now, invokeTime), cacheAfterExecution(now)))
  }

  def tryTriggerTokens(tokens: Set[Token[_]], ec: ExecutionCache): Unit = {
    for (k <- tokens if logic.allPrerequisitesMet(k, ec)) {
      trigger(k)
    }
  }

  def trigger(token: Token[_]): Unit = {
    executionStartTime(token) = clock.millis()
    val typedToken: Token[token.T0] = token.asInstanceOf[Token[token.T0]]
    context.actorOf(TokenEvaluationActor.props(typedToken, logic.implForToken(typedToken))) ! TokenEvaluationActor
      .Evaluate(mutableCache.fork)
  }

  def getExecutionSnapshot(now: Long, invokeTime: Option[Long]): ExecutionSnapshot = {
    val ec = mutableCache.fork
    execution.ExecutionSnapshot(logic, forToken, ec, executionStartTime.toMap -- ec.tokens, invokeTime, stateName, now)
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

  case object GetSnapshot

  def props[T](promise: Promise[ExecutionResult[T]],
               logic: Logic,
               initialCache: ExecutionCache,
               forToken: Token[T],
               clock: Clock): Props = {
    Props(new ExecutionActor[T](promise, logic, initialCache, forToken, clock))
  }

}
