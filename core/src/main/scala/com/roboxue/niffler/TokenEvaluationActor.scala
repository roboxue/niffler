package com.roboxue.niffler

import akka.actor.{Actor, Props}

import scala.util.Try

/**
  * @author rxue
  * @since 12/18/17.
  */
class TokenEvaluationActor[T](token: Token[T], impl: ImplementationDetails[T]) extends Actor {
  override def receive: Receive = {
    case TokenEvaluationActor.Evaluate(executionCache) =>
      val result = scala.concurrent.blocking {
        Try(impl.forceEvaluate(executionCache))
      }
      sender() ! TokenEvaluationActor.EvaluateComplete(token, result)
  }
}

object TokenEvaluationActor {

  case class Evaluate(executionCache: ExecutionCache)

  case class EvaluateComplete[T](token: Token[T], result: Try[T])

  def props[T](token: Token[T], impl: ImplementationDetails[T]): Props = {
    Props(new TokenEvaluationActor(token, impl))
  }

}
