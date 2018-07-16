package com.roboxue.niffler

import scala.concurrent.Future

/**
  * @author robert.xue
  * @since 7/15/18
  */
sealed trait Formula[T] {
  val dependsOn: Seq[TokenMeta]
  val outlet: Token[T]
}

case class Formula0[R](outlet: Token[R]) extends Formula[R] {
  override val dependsOn: Seq[Token[_]] = Seq.empty

  def :=(impl: => R): DataFlow[R] = implBy(impl)

  def implBy(impl: => R): DataFlow[R] = new SyncDataFlow[R](dependsOn, outlet, _ => impl)

  def :=>(futureImpl: => Future[R]): AsyncDataFlow[R] = implByFuture(futureImpl)

  def implByFuture(futureImpl: => Future[R]): AsyncDataFlow[R] =
    new AsyncDataFlow[R](dependsOn, outlet, _ => futureImpl)
}

case class Formula1[T1, R](t1: Token[T1], outlet: Token[R]) extends Formula[R] {
  override val dependsOn: Seq[Token[_]] = Seq(t1)

  def :=(impl: T1 => R): DataFlow[R] = implBy(impl)

  def implBy(impl: T1 => R): DataFlow[R] = new SyncDataFlow[R](dependsOn, outlet, state => impl(state(t1)))

  def :=>(futureImpl: T1 => Future[R]): DataFlow[R] = implByFuture(futureImpl)

  def implByFuture(futureImpl: T1 => Future[R]): DataFlow[R] =
    new AsyncDataFlow[R](dependsOn, outlet, state => futureImpl(state(t1)))
}

case class Formula2[T1, T2, R](t1: Token[T1], t2: Token[T2], outlet: Token[R]) extends Formula[R] {
  override val dependsOn: Seq[Token[_]] = Seq(t1, t2)

  def :=(impl: (T1, T2) => R): DataFlow[R] = implBy(impl)

  def implBy(impl: (T1, T2) => R): DataFlow[R] =
    new SyncDataFlow[R](dependsOn, outlet, state => impl(state(t1), state(t2)))

  def :=>(futureImpl: (T1, T2) => Future[R]): DataFlow[R] = implByFuture(futureImpl)

  def implByFuture(futureImpl: (T1, T2) => Future[R]): DataFlow[R] =
    new AsyncDataFlow[R](dependsOn, outlet, state => futureImpl(state(t1), state(t2)))
}
