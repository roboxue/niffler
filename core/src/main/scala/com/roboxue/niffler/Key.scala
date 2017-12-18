package com.roboxue.niffler

import java.util.UUID

/**
  * @author rxue
  * @since 12/15/17.
  */
case class Key[R](name: String, uuid: String = UUID.randomUUID().toString) {
  thisKey =>

  override def canEqual(that: Any): Boolean = {
    that.isInstanceOf[Key[R]]
  }

  override def equals(obj: scala.Any): Boolean = {
    canEqual(obj) && obj.asInstanceOf[Key[R]].uuid == uuid
  }

  def assign(value: => R): Implementation[R] =
    Implementation(thisKey, new ImplementationDetails[R] {
      override private[niffler] def forceEvaluate(cache: ExecutionCache): R = value

      override def dependency: Set[Key[_]] = Set.empty
    })

  def dependsOn[T1](k1: Key[T1])(f: (T1) => R): Implementation[R] =
    Implementation(thisKey, new ImplementationDetails[R] {
      override private[niffler] def forceEvaluate(cache: ExecutionCache): R = {
        f(cache(k1))
      }

      override def dependency: Set[Key[_]] = Set(k1)
    })

  def dependsOn[T1, T2](k1: Key[T1], k2: Key[T2])(f: (T1, T2) => R): Implementation[R] =
    Implementation(thisKey, new ImplementationDetails[R] {
      override private[niffler] def forceEvaluate(cache: ExecutionCache): R = {
        f(cache(k1), cache(k2))
      }

      override def dependency: Set[Key[_]] = Set(k1, k2)
    })

  def dependsOn[T1, T2, T3](k1: Key[T1], k2: Key[T2], k3: Key[T3])(f: (T1, T2, T3) => R): Implementation[R] =
    Implementation(thisKey, new ImplementationDetails[R] {
      override private[niffler] def forceEvaluate(cache: ExecutionCache): R = {
        f(cache(k1), cache(k2), cache(k3))
      }

      override def dependency: Set[Key[_]] = Set(k1, k2, k3)
    })
}
