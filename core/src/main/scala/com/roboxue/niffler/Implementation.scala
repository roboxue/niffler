package com.roboxue.niffler

/**
  * @author rxue
  * @since 12/15/17.
  */
case class Implementation[T] private[niffler] (token: Token[T], sketch: ImplementationLike[T])

abstract class DirectImplementation[T](val dependency: Set[Token[_]]) extends ImplementationLike[T] {
  private[niffler] def forceEvaluate(cache: ExecutionCache): T
}

abstract class IncrementalImplementation[T](val dependency: Set[Token[_]]) extends ImplementationLike[T] {
  private[niffler] def forceEvaluate(cache: ExecutionCache, existingValue: T): T

  final private[niffler] def merge(impl: DirectImplementation[T]): DirectImplementation[T] = {
    new DirectImplementation[T](dependency ++ impl.dependency) {
      override private[niffler] def forceEvaluate(cache: ExecutionCache): T = {
        IncrementalImplementation.this.forceEvaluate(cache, impl.forceEvaluate(cache))
      }
    }
  }
}

sealed trait ImplementationLike[T] {}
