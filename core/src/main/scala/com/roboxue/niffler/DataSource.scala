package com.roboxue.niffler

import scala.concurrent.Future

/**
  * @author robert.xue
  * @since 7/15/18
  */
trait DataSource[T] {
  val dependsOn: Seq[TokenMeta]

  def ~>(outlet: Token[T]): DataFlow[T] = writesTo(outlet)

  def writesTo(outlet: Token[T]): DataFlow[T]
}

final class SyncDataSource[T](val dependsOn: Seq[Token[_]], impl: ExecutionStateLike => T) extends DataSource[T] {
  override def writesTo(outlet: Token[T]): SyncDataFlow[T] = new SyncDataFlow(dependsOn, outlet, impl)
}

final class AsyncDataSource[T](val dependsOn: Seq[Token[_]], futureImpl: ExecutionStateLike => Future[T])
    extends DataSource[T] {
  override def writesTo(outlet: Token[T]): DataFlow[T] = new AsyncDataFlow(dependsOn, outlet, futureImpl)
}
