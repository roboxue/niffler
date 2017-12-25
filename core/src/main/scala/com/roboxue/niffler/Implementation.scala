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

abstract class DirectImplementation[T](val dependency: Set[Token[_]]) extends ImplementationLike[T] {
  private[niffler] def forceEvaluate(cache: ExecutionCache): T
}

abstract class IncrementalImplementation[T](val dependency: Set[Token[_]]) extends ImplementationLike[T] {
  private[niffler] def forceEvaluate(cache: ExecutionCache, existingValue: T): T

  final private[niffler] def mergeImpl(impl: DirectImplementation[T]): DirectImplementation[T] = {
    new DirectImplementation[T](dependency ++ impl.dependency) {
      override private[niffler] def forceEvaluate(cache: ExecutionCache): T = {
        IncrementalImplementation.this.forceEvaluate(cache, impl.forceEvaluate(cache))
      }
    }
  }

  final private[niffler] def amendCacheOf(token: Token[T]): DirectImplementation[T] = {
    new DirectImplementation[T](dependency) {
      override private[niffler] def forceEvaluate(cache: ExecutionCache): T = {
        IncrementalImplementation.this.forceEvaluate(cache, cache(token))
      }
    }
  }
}

sealed trait ImplementationLike[T] {}
