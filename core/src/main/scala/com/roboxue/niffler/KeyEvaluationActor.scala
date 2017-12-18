package com.roboxue.niffler

import akka.actor.Actor

import scala.util.Try

/**
  * @author rxue
  * @since 12/18/17.
  */
class KeyEvaluationActor[T](impl: Implementation[T]) extends Actor {
  override def receive: Receive = {
    case KeyEvaluationActor.Evaluate(executionCache) =>
      println(s"start ${impl.key.name}")
      val result = Try(impl.implementationDetails.forceEvaluate(executionCache))
      println(s"end ${impl.key.name}")
      sender() ! KeyEvaluationActor.EvaluateComplete(impl.key, result)
  }
}

object KeyEvaluationActor {

  case class Evaluate(executionCache: ExecutionCache)

  case class EvaluateComplete[T](key: Key[T], result: Try[T])

}
