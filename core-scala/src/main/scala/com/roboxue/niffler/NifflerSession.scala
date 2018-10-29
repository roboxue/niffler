package com.roboxue.niffler

import scala.collection.mutable

class NifflerSession {
  private val storage: mutable.Map[Token[_], Any] = mutable.Map.empty

  def get[T](token: Token[T]): T = {
    storage(token).asInstanceOf[T]
  }

  def set[T](token: Token[T], value: T): Unit = {
    storage(token) = value
  }
}
