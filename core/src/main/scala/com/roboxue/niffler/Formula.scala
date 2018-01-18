package com.roboxue.niffler

/**
  * Helper class to describe how to evaluate a value in runtime
  * Don't create this class directly. Use helper functions in [[Token.asFormula]], [[Token.mapFormula()]],
  * [[com.roboxue.niffler.syntax.Requires]] and [[com.roboxue.niffler.syntax.Constant]]
  *
  *
  * @param prerequisites the tokens that it depends on (has to finish evaluating before this starts)
  * @param evalF the function to execute when it's prerequisites has finished
  * @author rxue
  * @since 12/27/17.
  */
case class Formula[R](prerequisites: Set[Token[_]], evalF: ExecutionCache => R) {
  def apply(cache: ExecutionCache): R = evalF(cache)
}
