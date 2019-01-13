package com.roboxue.niffler

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author robert.xue
  * @since 7/15/18
  */
trait DataFlow[T] {
  val cleanUpPreviousDataFlow: Boolean
  val dependsOn: Seq[Token[_]]
  val outlet: Token[T]
  def evaluate(state: ExecutionStateLike)(implicit ex: ExecutionContext): Future[T]
}

final class SyncDataFlow[T](val dependsOn: Seq[Token[_]], val outlet: Token[T], impl: ExecutionStateLike => T)
    extends DataFlow[T] {
  override val cleanUpPreviousDataFlow: Boolean = true

  def evaluate(state: ExecutionStateLike)(implicit ex: ExecutionContext): Future[T] = {
    Future(impl(state))
  }
}

final class AsyncDataFlow[T](val dependsOn: Seq[Token[_]],
                             val outlet: Token[T],
                             futureImpl: ExecutionStateLike => Future[T])
    extends DataFlow[T] {
  override val cleanUpPreviousDataFlow: Boolean = true

  def evaluate(state: ExecutionStateLike)(implicit ex: ExecutionContext): Future[T] = {
    futureImpl(state)
  }
}
