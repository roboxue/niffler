package com.roboxue.niffler

import scala.collection.mutable

/**
  * @author rxue
  * @since 12/15/17.
  */
class ExecutionCache private (initialState: Map[Key[_], ExecutionCacheEntry[_]]) {
  private val storage = mutable.Map(initialState.toSeq: _*)

  private[niffler] def getStorage: Map[Key[_], ExecutionCacheEntry[_]] = storage.toMap

  def getValues: Map[Key[_], Any] = storage.mapValues(_.result).toMap

  def merge(that: ExecutionCache): ExecutionCache = {
    new ExecutionCache(getStorage ++ that.getStorage)
  }

  def fork: ExecutionCache = {
    new ExecutionCache(getStorage)
  }

  def hit(key: Key[_]): Boolean = {
    storage.contains(key)
  }

  def miss(key: Key[_]): Boolean = {
    !hit(key)
  }

  def apply[T](key: Key[T]): T = {
    storage(key).result.asInstanceOf[T]
  }

  def get[T](key: Key[T]): Option[T] = {
    storage.get(key).map(_.result.asInstanceOf[T])
  }

  def getOrElse[T](key: Key[T], default: => T): T = {
    storage.get(key).map(_.result.asInstanceOf[T]).getOrElse(default)
  }

  private[niffler] def store[T](key: Key[T], value: T, stats: KeyEvaluationStats): ExecutionCache = {
    storage(key) = ExecutionCacheEntry(value, stats)
    this
  }

  private[niffler] def evict[T](key: Key[T]): ExecutionCache = {
    storage.remove(key)
    this
  }
}

object ExecutionCache {
  def empty: ExecutionCache = new ExecutionCache(Map.empty)
}
