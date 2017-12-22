package com.roboxue.niffler

import java.util.UUID

import scala.reflect.runtime.universe.TypeTag

/**
  * @author rxue
  * @since 12/15/17.
  */
case class Token[R: TypeTag](name: String, uuid: String = UUID.randomUUID().toString)
    extends Token.AggregateOps[R]
    with Token.AssignOps[R]
    with Token.DependsOnOps[R] {

  /**
    * Used by external to reference [[R]]
    */
  type R0 = R

  lazy val returnTypeDescription: String = {
    import scala.reflect.runtime.universe._
    typeOf[R].toString
  }

  override def toString: String = s"$name[$returnTypeDescription]"

  def debugString: String = s"$name[$returnTypeDescription]($uuid)"

  override def canEqual(that: Any): Boolean = {
    that.isInstanceOf[Token[R]]
  }

  override def equals(obj: scala.Any): Boolean = {
    canEqual(obj) && obj.asInstanceOf[Token[R]].uuid == uuid
  }

  override def hashCode(): Int = {
    uuid.hashCode
  }

}

object Token {
  trait AggregateOps[R] {
    thisToken: Token[R] =>

    def aggregateWith[T1](k1: Token[T1])(f: (R, T1) => R): Implementation[R] =
      Implementation(thisToken, new IncrementalImplementation[R](Set(k1)) {
        override private[niffler] def forceEvaluate(cache: ExecutionCache, existingValue: R) = {
          f(existingValue, cache(k1))
        }
      })

    def aggregateWith[T1, T2](k1: Token[T1], k2: Token[T2])(f: (R, T1, T2) => R): Implementation[R] =
      Implementation(thisToken, new IncrementalImplementation[R](Set(k1, k2)) {
        override private[niffler] def forceEvaluate(cache: ExecutionCache, existingValue: R) = {
          f(existingValue, cache(k1), cache(k2))
        }
      })

    def aggregateWith[T1, T2, T3](k1: Token[T1], k2: Token[T2], k3: Token[T3])(
      f: (R, T1, T2, T3) => R
    ): Implementation[R] =
      Implementation(thisToken, new IncrementalImplementation[R](Set(k1, k2, k3)) {
        override private[niffler] def forceEvaluate(cache: ExecutionCache, existingValue: R) = {
          f(existingValue, cache(k1), cache(k2), cache(k3))
        }
      })
  }

  trait DependsOnOps[R] {
    thisToken: Token[R] =>
    def dependsOn[T1](k1: Token[T1])(f: (T1) => R): Implementation[R] =
      Implementation(thisToken, new DirectImplementation[R](Set(k1)) {
        override private[niffler] def forceEvaluate(cache: ExecutionCache): R = {
          f(cache(k1))
        }
      })

    def dependsOn[T1, T2](k1: Token[T1], k2: Token[T2])(f: (T1, T2) => R): Implementation[R] =
      Implementation(thisToken, new DirectImplementation[R](Set(k1, k2)) {
        override private[niffler] def forceEvaluate(cache: ExecutionCache): R = {
          f(cache(k1), cache(k2))
        }
      })

    def dependsOn[T1, T2, T3](k1: Token[T1], k2: Token[T2], k3: Token[T3])(f: (T1, T2, T3) => R): Implementation[R] =
      Implementation(thisToken, new DirectImplementation[R](Set(k1, k2, k3)) {
        override private[niffler] def forceEvaluate(cache: ExecutionCache): R = {
          f(cache(k1), cache(k2), cache(k3))
        }
      })

  }

  trait AssignOps[R] {
    thisToken: Token[R] =>

    def assign(value: => R): Implementation[R] =
      Implementation(thisToken, new DirectImplementation[R](Set.empty) {
        override private[niffler] def forceEvaluate(cache: ExecutionCache): R = value
      })
  }
}
