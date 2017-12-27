package com.roboxue.niffler

import com.roboxue.niffler.execution.Append

/**
  * Implementation is a typed binding of [[Token]] and [[ImplementationLike]]
  * Don't create this class directly.
  * Use helper methods in [[Token]] like [[Token.dependsOn]], [[Token.assign]]
  *
  * @param token the token whose implementation is being provided here
  * @param impl the implementation, can either be [[DirectImplementation]] or [[IncrementalImplementation]]
  * @author rxue
  * @since 12/15/17.
  */
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

sealed trait Implementation[T] {
  val token: Token[T]

  def dependency: Set[Token[_]]
}
