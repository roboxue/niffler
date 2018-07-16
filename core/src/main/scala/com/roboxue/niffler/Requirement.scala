package com.roboxue.niffler

import scala.concurrent.Future

/**
  * @author robert.xue
  * @since 7/15/18
  */
object Requires {
  def apply[T1](t1: Token[T1]): Requirement1[T1] = {
    Requirement1[T1](t1)
  }

  def apply[T1, T2](t1: Token[T1], t2: Token[T2]): Requirement2[T1, T2] = {
    Requirement2(t1, t2)
  }
}

sealed trait Requirement {
  val dependsOn: Seq[TokenMeta]
}

object Requirement0 extends Requirement {
  override val dependsOn: Seq[TokenMeta] = Seq.empty

  def ~>[Out](outlet: Token[Out]): Formula0[Out] = Formula0(outlet)

  def outputTo[Out](outlet: Token[Out]): Formula0[Out] = Formula0(outlet)
}

case class Requirement1[T1](t1: Token[T1]) extends Requirement {
  override val dependsOn: Seq[Token[_]] = Seq(t1)

  def ~>[Out](outlet: Token[Out]): Formula1[T1, Out] = Formula1(t1, outlet)

  def outputTo[Out](outlet: Token[Out]): Formula1[T1, Out] = Formula1(t1, outlet)

  def implBy[R](impl: T1 => R): DataSource[R] = new SyncDataSource[R](dependsOn, state => impl(state(t1)))

  def implByFuture[R](futureImpl: T1 => Future[R]): DataSource[R] =
    new AsyncDataSource[R](dependsOn, state => futureImpl(state(t1)))

}

case class Requirement2[T1, T2](t1: Token[T1], t2: Token[T2]) extends Requirement {
  override val dependsOn: Seq[Token[_]] = Seq(t1, t2)

  def ~>[Out](outlet: Token[Out]): Formula2[T1, T2, Out] = Formula2(t1, t2, outlet)

  def outputTo[Out](outlet: Token[Out]): Formula2[T1, T2, Out] = Formula2(t1, t2, outlet)

  def :=[R](impl: (T1, T2) => R): DataSource[R] = implBy(impl)

  def :=>[R](futureImpl: (T1, T2) => Future[R]): DataSource[R] = implByFuture(futureImpl)

  def implBy[R](impl: (T1, T2) => R): DataSource[R] =
    new SyncDataSource[R](dependsOn, state => impl(state(t1), state(t2)))

  def implByFuture[R](futureImpl: (T1, T2) => Future[R]): DataSource[R] =
    new AsyncDataSource[R](dependsOn, state => futureImpl(state(t1), state(t2)))
}
