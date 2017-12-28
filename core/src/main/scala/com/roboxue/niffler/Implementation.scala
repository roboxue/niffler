package com.roboxue.niffler

import com.roboxue.niffler.execution.Append

/**
  * Implementation is a typed binding of [[Token]] and [[TokenEvaluation]]
  * Don't create this class directly.
  * Use helper methods in [[Token]] like [[Token.dependsOn]], [[Token.assign]], [[Token.amendWith]],
  * or in [[Niffler]] like [[Niffler.constant]], [[Niffler.evalToken]] or [[Niffler.evalTokens]]
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
  def dependency: Set[Token[_]]
}

case class DirectImplementation[T] private[niffler] (token: Token[T], eval: TokenEvaluation[T])
    extends Implementation[T] {
  override def dependency: Set[Token[_]] = eval.dependency
}

case class IncrementalImplementation[T, R] private[niffler] (token: Token[T],
                                                             eval: TokenEvaluation[R],
                                                             amendable: Append.Value[T, R])
    extends Implementation[T] {
  override def dependency: Set[Token[_]] = eval.dependency

  def merge(existingImpl: Option[DirectImplementation[T]]): DirectImplementation[T] = {
    val newDependency: Set[Token[_]] = dependency ++ existingImpl.map(_.dependency).getOrElse(Set.empty)
    DirectImplementation(token, TokenEvaluation(newDependency, (cache) => {
      val existingValue: T = existingImpl.map(_.eval(cache)).getOrElse(cache.get(token).getOrElse(amendable.empty))
      val newValue: R = eval(cache)
      amendable.appendValue(existingValue, newValue)
    }))
  }
}
