package com.roboxue.niffler.execution

import com.roboxue.niffler.Token

/**
  * Classes that represent the key events during [[ExecutionActor]]'s evaluation
  * @author rxue
  * @since 1/20/18.
  */
abstract class TimelineEvent(_eventType: String) {
  val eventType: String = _eventType
  val token: Token[_]
  val time: Long
}

object TimelineEvent {
  import scala.language.existentials
  case class CacheHit(token: Token[_], time: Long) extends TimelineEvent("cached")
  case class EvaluationBlocked(token: Token[_], time: Long) extends TimelineEvent("blocked")
  case class EvaluationStarted(token: Token[_], time: Long) extends TimelineEvent("started")
  case class EvaluationEnded(token: Token[_], time: Long) extends TimelineEvent("ended")
  case class EvaluationCancelled(token: Token[_], time: Long, reason: String) extends TimelineEvent("cancelled")
  case class EvaluationFailed(token: Token[_], time: Long, ex: Throwable) extends TimelineEvent("failed")
}
