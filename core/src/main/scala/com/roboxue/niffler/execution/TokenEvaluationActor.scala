package com.roboxue.niffler.execution

import akka.actor.{Actor, Props}
import com.roboxue.niffler.{RegularOperation, ExecutionCache, Token}

import scala.util.Try

/**
  * Communicate with [[ExecutionActor]] only, this actor is responsible of evaluating a single [[Token]]
  * @author rxue
  * @since 12/18/17.
  */
class TokenEvaluationActor[T](token: Token[T], impl: RegularOperation[T]) extends Actor {
  override def receive: Receive = {
    case TokenEvaluationActor.Evaluate(executionCache) =>
      // use blocking here to indicate a potential very long process
      val result = scala.concurrent.blocking {
        Try(impl.formula(executionCache))
      }
      sender() ! TokenEvaluationActor.EvaluateComplete(token, result)
  }
}

object TokenEvaluationActor {

  case class Evaluate(executionCache: ExecutionCache)

  case class EvaluateComplete[T](token: Token[T], result: Try[T])

  def props[T](token: Token[T], impl: RegularOperation[T]): Props = {
    Props(new TokenEvaluationActor(token, impl))
  }

}
