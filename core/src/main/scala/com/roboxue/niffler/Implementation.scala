package com.roboxue.niffler

import com.roboxue.niffler.execution.Append

/**
  * Implementation is a typed binding of [[Token]] and [[TokenEvaluation]]
  * Don't create this class directly.
  * Use helper methods in [[Token]] like [[Token.assign]],
  * [[Token.amendWith]], [[Token.amendWithToken]], [[Token.dependsOn]], [[Token.dependsOnToken]]
  * or in [[Niffler]] like [[Niffler.constant]] or [[Niffler.evalTokens]]
  *
  * @author rxue
  * @since 12/15/17.
  */
sealed trait Implementation[T] {

  /**
    * the token whose implementation is being provided here
    */
  val token: Token[T]

  /**
    * the token that needs to be evaluated before this can be evaluated
    * @return
    */
  def prerequisites: Set[Token[_]]
}

case class DirectImplementation[T] private[niffler] (token: Token[T], eval: TokenEvaluation[T])
    extends Implementation[T] {
  override def prerequisites: Set[Token[_]] = eval.prerequisites
}

case class IncrementalImplementation[T, R] private[niffler] (token: Token[T],
                                                             eval: TokenEvaluation[R],
                                                             amendable: Append.Value[T, R])
    extends Implementation[T] {
  override def prerequisites: Set[Token[_]] = eval.prerequisites

  def merge(existingImpl: Option[DirectImplementation[T]]): DirectImplementation[T] = {
    val mergedPrerequisites: Set[Token[_]] = prerequisites ++ existingImpl.map(_.prerequisites).getOrElse(Set.empty)
    DirectImplementation(token, TokenEvaluation(mergedPrerequisites, (cache) => {
      val existingValue: T = existingImpl.map(_.eval(cache)).getOrElse(cache.get(token).getOrElse(amendable.empty))
      val newValue: R = eval(cache)
      amendable.appendValue(existingValue, newValue)
    }))
  }
}
