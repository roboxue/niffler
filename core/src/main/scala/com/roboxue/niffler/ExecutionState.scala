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
}

class MutableExecutionState private[niffler] (initialState: Map[Token[_], Any]) extends ExecutionStateLike {
  val _storage: mutable.Map[Token[_], Any] = mutable.Map(initialState.toSeq: _*)

  override protected def storage: Map[Token[_], Any] = _storage

  def update[T](token: Token[T], value: T): Unit = {
    _storage(token) = value
  }
}

class ExecutionState private (initialState: Map[Token[_], Any]) extends ExecutionStateLike {
  override protected def storage: Map[Token[_], Any] = initialState

  def set[T](token: Token[T], value: T): ExecutionState = {
    new ExecutionState(initialState.updated(token, value))
  }

  def mutableCopy: MutableExecutionState = new MutableExecutionState(storage)
}

object ExecutionState {
  def empty: ExecutionState = new ExecutionState(Map.empty)
}
