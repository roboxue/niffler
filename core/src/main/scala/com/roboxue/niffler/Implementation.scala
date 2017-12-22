package com.roboxue.niffler

/**
  * @author rxue
  * @since 12/15/17.
  */
sealed case class Implementation[T](token: Token[T], sketch: ImplementationSketch[T])

abstract class ImplementationDetails[T](val dependency: Set[Token[_]]) extends ImplementationSketch[T] {
  private[niffler] def forceEvaluate(cache: ExecutionCache): T
}

abstract class ImplementationIncrement[T](val dependency: Set[Token[_]]) extends ImplementationSketch[T] {
  private[niffler] def forceEvaluate(cache: ExecutionCache, existingValue: T): T

  final private[niffler] def merge(impl: ImplementationDetails[T]): ImplementationDetails[T] = {
    new ImplementationDetails[T](dependency ++ impl.dependency) {
      override private[niffler] def forceEvaluate(cache: ExecutionCache): T = {
        ImplementationIncrement.this.forceEvaluate(cache, impl.forceEvaluate(cache))
      }
    }
  }
}

sealed trait ImplementationSketch[T] {}
