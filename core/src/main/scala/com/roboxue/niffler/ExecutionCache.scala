package com.roboxue.niffler

import scala.collection.mutable

/**
  * @author rxue
  * @since 12/15/17.
  */
class MutableExecutionCache(initialState: Map[Key[_], ExecutionCacheEntry[_]]) {
  private val storage = mutable.Map(initialState.toSeq: _*)

  def keys: Iterable[Key[_]] = storage.keys

  private[niffler] def getStorage: Map[Key[_], ExecutionCacheEntry[_]] = storage.toMap

  def getValues: Map[Key[_], Any] = storage.mapValues(_.result).toMap

  def omit(keys: Set[Key[_]]): ExecutionCache = {
    new ExecutionCache(getStorage.filterKeys(p => !keys.contains(p)))
  }

  def invalidateTtlCache(now: Long): Unit = {
    storage.retain({
      case (_, value) =>
        value.ttl.isEmpty || now > value.stats.completeTime + value.ttl.get
    })
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

  def fork: ExecutionCache = {
    ExecutionCache(getStorage)
  }

  def store[T](key: Key[T], value: T, stats: KeyEvaluationStats, ttl: Option[Long]): Unit = {
    storage(key) = ExecutionCacheEntry(value, stats, ttl)
  }

  def evict[T](key: Key[T]): Unit = {
    storage.remove(key)
  }
}

case class ExecutionCache(storage: Map[Key[_], ExecutionCacheEntry[_]]) {
  def keys: Iterable[Key[_]] = storage.keys

  def getValues: Map[Key[_], Any] = storage.mapValues(_.result)

  def merge(that: ExecutionCache): ExecutionCache = {
    ExecutionCache(storage ++ that.storage)
  }

  def mutableFork: MutableExecutionCache = {
    new MutableExecutionCache(storage)
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
}

object ExecutionCache {
  val empty: ExecutionCache = new ExecutionCache(Map.empty)
}
