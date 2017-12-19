package com.roboxue.niffler

import java.time.Clock

import akka.actor.Actor

import scala.util.Try

/**
  * @author rxue
  * @since 12/18/17.
  */
class KeyEvaluationActor[T](impl: Implementation[T], clock: Clock = Clock.systemUTC()) extends Actor {
  override def receive: Receive = {
    case KeyEvaluationActor.Evaluate(executionCache) =>
      val start = clock.millis()
      val result = Try(impl.implementationDetails.forceEvaluate(executionCache))
      val end = clock.millis()
      sender() ! KeyEvaluationActor.EvaluateComplete(impl.key, result, KeyEvaluationStats(start, end))
  }
}

object KeyEvaluationActor {

  case class Evaluate(executionCache: ExecutionCache)

  case class EvaluateComplete[T](key: Key[T], result: Try[T], stats: KeyEvaluationStats)

}
