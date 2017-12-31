package com.roboxue.niffler.execution

import com.roboxue.niffler.execution.ExecutionCacheEntryType._
import com.roboxue.niffler.{ExecutionCache, Logic, Token}

import scala.language.existentials

/**
  * @param logic the logic being executed
  * @param tokenToEvaluate the token to be evaluated
  * @param cache internal cache during execution
  * @param ongoing tokens still being evaluated
  * @param invocationTime evaluation started
  * @param executionStatus the [[ExecutionStatus]]
  * @param asOfTime snapshot generation time
  * @author rxue
  * @since 12/19/17.
  */
case class ExecutionSnapshot(logic: Logic,
                             tokenToEvaluate: Token[_],
                             cache: ExecutionCache,
                             ongoing: Map[Token[_], Long],
                             invocationTime: Option[Long],
                             executionStatus: ExecutionStatus,
                             asOfTime: Long) {

  /**
    * Utility function during debuging
    */
  def printTimeLine(): Unit = {
    implicit object ExecutionCacheEntryTypeOrdering extends Ordering[ExecutionCacheEntryType] {
      override def compare(x: ExecutionCacheEntryType, y: ExecutionCacheEntryType): Int = {
        (x, y) match {
          case (Inherited, Inherited) | (Injected, Injected) =>
            0
          case (Inherited, Injected) | (Injected, TokenEvaluationStats(_, _)) =>
            -1
          case (Injected, Inherited) | (TokenEvaluationStats(_, _), Injected) =>
            1
          case (l: TokenEvaluationStats, r: TokenEvaluationStats) if l.startTime == r.startTime =>
            l.completeTime compare l.completeTime
          case (l: TokenEvaluationStats, r: TokenEvaluationStats) =>
            l.startTime compare r.startTime
        }
      }
    }

    val (beforeInvocation, afterInvocation) =
      cache.storage.toSeq.partition(pair => {
        invocationTime.isEmpty || pair._2.entryType == Inherited || pair._2.entryType == Injected
      })
    if (beforeInvocation.nonEmpty) {
      println("reused cache:")
      for ((key, value) <- beforeInvocation.sortBy(_._2.entryType)) {
        printTokenValuePair(key, value)
      }
    }
    if (afterInvocation.nonEmpty) {
      println("evaluation:")
      for ((key, value) <- afterInvocation.sortBy(_._2.entryType)) {
        printTokenValuePair(key, value)
      }
    }
    if (ongoing.nonEmpty) {
      println("on going:")
      for ((key, value) <- ongoing.toSeq.sortBy(_._2)) {
        printTokenAndTime(key, value)
      }
    }
  }

  private def printToken(token: Token[_]): Unit = {
    if (token == tokenToEvaluate) {
      print(s"[*]\t${token.name}: ")
    } else {
      print(s"\t${token.name}: ")
    }
  }

  private def printTokenValuePair(token: Token[_], result: ExecutionCacheEntry[_]): Unit = {
    printToken(token)
    print(result.entryType)
    result.ttl match {
      case Some(ttl) if result.entryType.isInstanceOf[TokenEvaluationStats] =>
        println(s" ttl: ${result.entryType.asInstanceOf[TokenEvaluationStats].completeTime + ttl}")
      case None =>
        println()
    }
  }

  private def printTokenAndTime(token: Token[_], time: Long): Unit = {
    printToken(token)
    println(s"$time")
  }
}
