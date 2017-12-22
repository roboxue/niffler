package com.roboxue.niffler.execution

import akka.actor.{Actor, Props}
import com.roboxue.niffler.{DirectImplementation, ExecutionCache, Token}

import scala.util.Try

/**
  * @author rxue
  * @since 12/18/17.
  */
class TokenEvaluationActor[T](token: Token[T], impl: DirectImplementation[T]) extends Actor {
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

  def props[T](token: Token[T], impl: DirectImplementation[T]): Props = {
    Props(new TokenEvaluationActor(token, impl))
  }

}
