package com.roboxue.niffler.execution

import java.time.Clock

import akka.actor.{FSM, Props}
import com.roboxue.niffler.execution.ExecutionCacheEntryType.TokenEvaluationStats
import com.roboxue.niffler.execution.ExecutionStatus._
import com.roboxue.niffler.{execution, _}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
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
  val timelineEvents: ListBuffer[TimelineEvent] = ListBuffer()

  startWith(Unstarted, None)

  when(Unstarted) {
    case Event(ExecutionActor.Invoke, _) =>
      val invokeTime = clock.millis()
      // Check TTL during invoke, invalidate
      mutableCache.invalidateTtlCache(invokeTime)
      // calculate the tokens that need to be recalculated during this round of invocation
      val ec = mutableCache.omit(Set(forToken))

      val visited: mutable.Set[Token[_]] = mutable.Set.empty
      var visitThisBatch: Seq[Token[_]] = Seq(forToken)
      val canTriggerImmediately = ListBuffer.empty[Token[_]]
      // schedule forToken
      while (visitThisBatch.nonEmpty) {
        val visitNextBatch = mutable.Set.empty[Token[_]]
        for (t <- visitThisBatch) {
          val predecessors = logic.getPredecessors(t)
          val unmetPredecessors = ListBuffer.empty[Token[_]]
          for (p <- predecessors) {
            if (ec.hit(p)) {
              if (!visited.contains(p)) {
                timelineEvents += TimelineEvent.CacheHit(p, clock.millis())
                visited += p
              }
            } else {
              if (!visited.contains(p)) {
                timelineEvents += TimelineEvent.ScheduledToExecute(p, clock.millis())
                visited += p
              }
              visitNextBatch += p
              unmetPredecessors += p
            }
          }
          if (unmetPredecessors.isEmpty) {
            canTriggerImmediately += t
          } else {
            unmetPrerequisites += t
          }
        }
        visitThisBatch = visitNextBatch.toSeq
      }
      // trigger eval of prerequisites
      if (canTriggerImmediately.contains(forToken)) {
        trigger(forToken)
      } else {
        tryTriggerTokens(canTriggerImmediately.toSet, ec)
      }
      goto(Running) using Some(invokeTime)
    case Event(ExecutionActor.GetSnapshot, _) =>
      sender() ! getExecutionSnapshot(clock.millis(), None)
      stay()
    case Event(ExecutionActor.Cancel(reason), _) =>
      handleCancelRequest(reason)
      goto(Cancelled)
  }

  when(Running) {
    case Event(TokenEvaluationActor.EvaluateComplete(token, tryResult), invokeTime) =>
      tryResult match {
        case Failure(ex) =>
          val now = clock.millis()
          timelineEvents += TimelineEvent.EvaluationFailed(token, now, ex)
          announceFailure(token, ex, now, invokeTime)
          handleCancelRequest("failure during execution")
          goto(Failed)
        case Success(result) =>
          val now = clock.millis()
          timelineEvents += TimelineEvent.EvaluationEnded(token, now)
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
            val canTrigger = logic.getSuccessors(token).intersect(unmetPrerequisites)
            tryTriggerTokens(canTrigger, mutableCache.fork)
            stay()
          }
      }
    case Event(ExecutionActor.GetSnapshot, invokeTime) =>
      sender() ! getExecutionSnapshot(clock.millis(), invokeTime)
      stay()
    case Event(ExecutionActor.Cancel(reason), _) =>
      handleCancelRequest(reason)
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
    val now = clock.millis()
    executionStartTime(token) = now
    timelineEvents += TimelineEvent.EvaluationStarted(token, now)
    val typedToken: Token[token.T0] = token.asInstanceOf[Token[token.T0]]
    context.actorOf(TokenEvaluationActor.props(typedToken, logic.implForToken(typedToken))) ! TokenEvaluationActor
      .Evaluate(mutableCache.fork)
  }

  def getExecutionSnapshot(now: Long, invokeTime: Option[Long]): ExecutionSnapshot = {
    val ec = mutableCache.fork
    execution.ExecutionSnapshot(
      logic,
      forToken,
      ec,
      executionStartTime.toMap -- ec.tokens,
      invokeTime,
      stateName,
      timelineEvents.toList,
      now
    )
  }

  def cacheAfterExecution(now: Long): ExecutionCache = {
    val tokensCachedOnlyWithinExecution =
      logic.tokensInvolved.filter(k => logic.cachingPolicy(k) == CachingPolicy.WithinExecution)
    mutableCache.invalidateTtlCache(now)
    mutableCache.omit(tokensCachedOnlyWithinExecution)
  }

  def handleCancelRequest(reason: String): Unit = {
    val now = clock.millis()
    for (token <- executionStartTime.keys) {
      timelineEvents += TimelineEvent.EvaluationCancelled(token, now, reason)
    }
  }
}

object ExecutionActor {

  case object Invoke

  case class Cancel(reason: String)

  case object GetSnapshot

  def props[T](promise: Promise[ExecutionResult[T]],
               logic: Logic,
               initialCache: ExecutionCache,
               forToken: Token[T],
               clock: Clock): Props = {
    Props(new ExecutionActor[T](promise, logic, initialCache, forToken, clock))
  }

}

abstract class TimelineEvent(_eventType: String) {
  val eventType: String = _eventType
  val token: Token[_]
  val time: Long
}

object TimelineEvent {
  import scala.language.existentials
  case class CacheHit(token: Token[_], time: Long) extends TimelineEvent("cached")
  case class ScheduledToExecute(token: Token[_], time: Long) extends TimelineEvent("scheduled")
  case class EvaluationStarted(token: Token[_], time: Long) extends TimelineEvent("started")
  case class EvaluationEnded(token: Token[_], time: Long) extends TimelineEvent("ended")
  case class EvaluationCancelled(token: Token[_], time: Long, reason: String) extends TimelineEvent("cancelled")
  case class EvaluationFailed(token: Token[_], time: Long, ex: Throwable) extends TimelineEvent("failed")
}
