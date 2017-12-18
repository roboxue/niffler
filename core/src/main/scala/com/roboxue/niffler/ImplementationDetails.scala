package com.roboxue.niffler

/**
  * @author rxue
  * @since 12/18/17.
  */
trait ImplementationDetails[T] {
  def dependency: Set[Key[_]]
  private[niffler] def forceEvaluate(cache: ExecutionCache): T
}
