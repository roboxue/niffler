package com.roboxue.niffler

import scala.collection.{Map, mutable}

/**
  * @author robert.xue
  * @since 7/15/18
  */
trait ExecutionStateLike {
  protected def storage: Map[Token[_], Any]

  def apply[T](token: Token[T]): T = storage(token).asInstanceOf[T]

  def get[T](token: Token[T]): Option[T] = storage.get(token).map(_.asInstanceOf[T])

  def contains(token: Token[_]): Boolean = storage.contains(token)

  def keySet: collection.Set[Token[_]] = storage.keySet

  def mutableCopy: MutableExecutionState = new MutableExecutionState(storage)

  def seal: ExecutionState = new ExecutionState(storage)
}

class MutableExecutionState private[niffler] (initialState: Map[Token[_], Any]) extends ExecutionStateLike {
  private[niffler] val _storage: mutable.Map[Token[_], Any] = mutable.Map(initialState.toSeq: _*)

  override protected def storage: Map[Token[_], Any] = _storage

  def put[T](token: Token[T], value: T): MutableExecutionState = {
    _storage(token) = value
    this
  }

}

class ExecutionState private[niffler] (initialState: Map[Token[_], Any]) extends ExecutionStateLike {
  override protected def storage: Map[Token[_], Any] = initialState

  def set[T](token: Token[T], value: T): ExecutionState = {
    new ExecutionState(initialState.updated(token, value))
  }

}

object ExecutionState {
  def empty: ExecutionState = new ExecutionState(Map.empty)

  def emptyMutable: MutableExecutionState = new MutableExecutionState(Map.empty)
}

class ExecutionStateTracker(initialState: ExecutionStateLike = ExecutionState.empty) {
  private var _executionState: ExecutionStateLike = initialState
  def getExecutionState: ExecutionStateLike = _executionState
  def setExecutionState(state: ExecutionStateLike): Unit = {
    _executionState = state
  }
}
