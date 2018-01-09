package com.roboxue.niffler

/**
  * @author rxue
  * @since 12/27/17.
  */
case class Formula[R](prerequisites: Set[Token[_]], evalF: ExecutionCache => R) {
  def apply(cache: ExecutionCache): R = evalF(cache)
}
