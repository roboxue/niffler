package com.roboxue.niffler.execution

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
    implicit object TokenEvaluationStatsOrdering extends Ordering[TokenEvaluationStats] {
      override def compare(x: TokenEvaluationStats, y: TokenEvaluationStats): Int = {
        val start = x.startTime compare y.startTime
        if (start == 0) {
          x.completeTime compare y.completeTime
        } else {
          start
        }
      }
    }

    val (beforeInvocation, afterInvocation) =
      cache.storage.toSeq.partition(invocationTime.isEmpty || _._2.stats.completeTime < invocationTime.get)
    if (beforeInvocation.nonEmpty) {
      println("reused cache:")
      for ((key, value) <- beforeInvocation.sortBy(_._2.stats)) {
        printTokenValuePair(key, value)
      }
    }
    if (afterInvocation.nonEmpty) {
      println("evaluation:")
      for ((key, value) <- afterInvocation.sortBy(_._2.stats)) {
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
    print(s"${result.stats.startTime} -> ${result.stats.completeTime}")
    result.ttl match {
      case Some(ttl) =>
        println(s" ttl: ${result.stats.completeTime + ttl}")
      case None =>
        println()
    }
  }

  private def printTokenAndTime(token: Token[_], time: Long): Unit = {
    printToken(token)
    println(s"$time")
  }
}
