package com.roboxue.niffler

/**
  * [[DataFlowOperation]] is a typed binding of [[Token]] and [[Formula]]
  * Don't create this class directly.
  * Use helper methods in [[Token]] like [[Token.:=]], [[Token.+=]]
  *
  * @author rxue
  * @since 12/15/17.
  */
trait DataFlowOperation[T] {

  /**
    * this [[Token]] wil be represented by the value calculated in runtime
    */
  val token: Token[T]

  /**
    * the [[Token]]s that need to be evaluated before this [[DataFlowOperation]] can be evaluated
    */
  def prerequisites: Set[Token[_]]
}
