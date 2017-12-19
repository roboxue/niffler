package com.roboxue.niffler
import scala.language.existentials

/**
  * @author rxue
  * @since 12/19/17.
  */
case class ExecutionSnapshot(logic: Logic,
                             keyToEvaluate: Key[_],
                             cache: ExecutionCache,
                             ongoing: Map[Key[_], Long],
                             startTime: Long)
