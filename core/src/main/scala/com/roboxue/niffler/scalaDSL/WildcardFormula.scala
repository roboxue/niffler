package com.roboxue.niffler.scalaDSL
import com.roboxue.niffler.{AsyncDataFlow, DataFlow, SyncDataFlow, Token}

import scala.concurrent.Future

/**
  * @author robert.xue
  * @since 2019-04-01
  */
case class WildcardFormula[R, Z](dependencies: Seq[Token[Z]], outlet: Token[R]) extends Formula[R] {
  override val dependsOn: Seq[Token[_]] = dependencies

  def :=(impl: Seq[Z] => R): DataFlow[R] = implBy(impl)

  def implBy(impl: Seq[Z] => R): DataFlow[R] =
    new SyncDataFlow[R](dependsOn, outlet, state => {
      impl(dependencies.map(state.apply))
    })

  def :=>(futureImpl: Seq[Z] => Future[R]): AsyncDataFlow[R] = implByFuture(futureImpl)

  def implByFuture(futureImpl: Seq[Z] => Future[R]): AsyncDataFlow[R] =
    new AsyncDataFlow[R](dependsOn, outlet, state => {
      futureImpl(dependencies.map(state.apply))
    })
}
