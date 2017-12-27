package com.roboxue.niffler

/**
  * Implementation is a typed binding of [[Token]] and [[ImplementationLike]]
  * Don't create this class directly.
  * Use helper methods in [[Token]] like [[Token.dependsOn]], [[Token.assign]] or [[Token.amend]]
  *
  * @param token the token whose implementation is being provided here
  * @param impl the implementation, can either be [[DirectImplementation]] or [[IncrementalImplementation]]
  * @author rxue
  * @since 12/15/17.
  */
case class Implementation[T] private[niffler] (token: Token[T], impl: ImplementationLike[T])

case class DirectImplementation[T] private[niffler] (dependency: Set[Token[_]], eval: ExecutionCache => T)
    extends ImplementationLike[T]

object DirectImplementation {
  def apply[T1, T2, R](t1: Token[T1], t2: Token[T2])(impl: (T1, T2) => R): DirectImplementation[R] = {
    new DirectImplementation(Set(t1, t2), (cache) => impl(cache(t1), cache(t2)))
  }
}

abstract class IncrementalImplementation[T](val dependency: Set[Token[_]]) extends ImplementationLike[T] {
  private[niffler] def forceEvaluate(cache: ExecutionCache, existingValue: T): T

  final private[niffler] def mergeImpl(impl: DirectImplementation[T]): DirectImplementation[T] = {
    new DirectImplementation[T](dependency ++ impl.dependency, cache => forceEvaluate(cache, impl.eval(cache)))
  }

  final private[niffler] def amendCacheOf(token: Token[T]): DirectImplementation[T] = {
    new DirectImplementation[T](dependency, cache => forceEvaluate(cache, cache(token)))
  }
}

sealed trait ImplementationLike[T] {}
