package com.roboxue.niffler

/**
  * [[RegularOperation]] is the default implementation of [[DataFlowOperation]].
  *
  * To create a [[RegularOperation]], use helper methods in [[com.roboxue.niffler.syntax.Requires]] or
  * [[Token]](via [[com.roboxue.niffler.syntax.TokenSyntax]]
  *
  * @param token   this [[Token]] wil be represented by the value calculated using [[formula]] in runtime
  * @param formula the [[Formula]] used to calculate the [[token]]'s value
  * @tparam T [[Token]]'s data type
  * @author rxue
  * @since 12/15/17.
  */
case class RegularOperation[T] private[niffler] (token: Token[T], formula: Formula[T]) extends DataFlowOperation[T] {
  override def prerequisites: Set[Token[_]] = formula.prerequisites
}
