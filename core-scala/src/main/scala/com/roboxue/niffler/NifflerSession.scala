package com.roboxue.niffler

import scala.collection.mutable

case class NifflerSession(sessionId: Long, startTime: Long) {
  private val storage: mutable.Map[Token[_], Any] = mutable.Map.empty

  def get[T](token: Token[T]): T = {
    storage(token).asInstanceOf[T]
  }

  def set[T](token: Token[T], value: T): Unit = {
    storage(token) = value
  }

  def contains(token: Token[_]): Boolean = {
    storage.contains(token)
  }
}
