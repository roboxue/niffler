package com.roboxue.niffler.execution

import com.roboxue.niffler.{ExecutionCache, Logic, Token}

import scala.language.existentials

/**
  * @param logic the logic being executed
  * @param tokenToEvaluate the token to be evaluated
  * @param cache current internal cache during execution
  * @param ongoing tokens being evaluated
  * @param startTime evaluation started
  * @param asOfTime snapshot generation time
  * @author rxue
  * @since 12/19/17.
  */
case class ExecutionSnapshot(logic: Logic,
                             tokenToEvaluate: Token[_],
                             cache: ExecutionCache,
                             ongoing: Map[Token[_], Long],
                             startTime: Long,
                             asOfTime: Long)
