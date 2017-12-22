package com.roboxue.niffler

import akka.actor.Actor

import scala.util.Try

/**
  * @author rxue
  * @since 12/18/17.
  */
class KeyEvaluationActor[T](key: Key[T], impl: ImplementationDetails[T]) extends Actor {
  override def receive: Receive = {
    case KeyEvaluationActor.Evaluate(executionCache) =>
      val result = scala.concurrent.blocking {
        Try(impl.forceEvaluate(executionCache))
      }
      sender() ! KeyEvaluationActor.EvaluateComplete(key, result)
  }
}

object KeyEvaluationActor {

  case class Evaluate(executionCache: ExecutionCache)

  case class EvaluateComplete[T](key: Key[T], result: Try[T])

}
