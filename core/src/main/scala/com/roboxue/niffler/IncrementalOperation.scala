package com.roboxue.niffler

import com.roboxue.niffler.execution.Append

/**
  * [[IncrementalOperation]] is an advanced implementation of [[DataFlowOperation]].
  *
  * To create a [[IncrementalOperation]], use helper methods [Token.+=]]
  *
  * @param token this [[Token]] wil be represented by the value calculated using [[formula]] and [[howToAmend]] in runtime
  * @param formula the [[Formula]] used to calculate the amendment to [[token]]'s existing value
  * @param howToAmend the function used to append the existing value typed [[T]] with [[formula]]'s result typed [[R]]
  * @author rxue
  * @since 12/15/17.
  */
case class IncrementalOperation[T, R] private[niffler] (token: Token[T],
                                                        formula: Formula[R],
                                                        howToAmend: Append.Value[T, R])
    extends DataFlowOperation[T] {
  def merge(existingImpl: Option[RegularOperation[T]]): RegularOperation[T] = {
    val mergedPrerequisites: Set[Token[_]] = prerequisites ++ existingImpl.map(_.prerequisites).getOrElse(Set.empty)
    RegularOperation(token, Formula(mergedPrerequisites, (cache) => {
      val existingValue: T = existingImpl.map(_.formula(cache)).getOrElse(cache.get(token).getOrElse(howToAmend.empty))
      val newValue: R = formula(cache)
      howToAmend.appendValue(existingValue, newValue)
    }))
  }

  override def prerequisites: Set[Token[_]] = formula.prerequisites
}
