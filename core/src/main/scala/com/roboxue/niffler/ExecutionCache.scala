package com.roboxue.niffler

import com.roboxue.niffler.execution.{ExecutionCacheEntry, TokenEvaluationStats}

import scala.collection.mutable

/**
  * @author rxue
  * @since 12/15/17.
  */
case class ExecutionCache(storage: Map[Token[_], ExecutionCacheEntry[_]]) {
  def tokens: Set[Token[_]] = storage.keySet

  def getValues: Map[Token[_], Any] = storage.mapValues(_.result)

  def merge(that: ExecutionCache): ExecutionCache = {
    ExecutionCache(storage ++ that.storage)
  }

  def mutableFork: MutableExecutionCache = {
    new MutableExecutionCache(storage)
  }

  def hit(token: Token[_]): Boolean = {
    storage.contains(token)
  }

  def miss(token: Token[_]): Boolean = {
    !hit(token)
  }

  def apply[T](token: Token[T]): T = {
    storage(token).result.asInstanceOf[T]
  }

  def get[T](token: Token[T]): Option[T] = {
    storage.get(token).map(_.result.asInstanceOf[T])
  }

  def getOrElse[T](token: Token[T], default: => T): T = {
    storage.get(token).map(_.result.asInstanceOf[T]).getOrElse(default)
  }
}

object ExecutionCache {
  val empty: ExecutionCache = new ExecutionCache(Map.empty)

  /**
    * Creating an [[ExecutionCache]] manually is not type safe, thus this is marked as private and used only in tests
    *
    * @param map a typesafe map, so that every value conforms to the type parameter of the key token
    * @return
    */
  private[niffler] def fromValue(map: Map[Token[_], Any]): ExecutionCache = {
    ExecutionCache(map.mapValues(v => ExecutionCacheEntry(v)))
  }
}

class MutableExecutionCache(initialState: Map[Token[_], ExecutionCacheEntry[_]]) {
  private val storage = mutable.Map(initialState.toSeq: _*)

  def tokens: Iterable[Token[_]] = storage.keys

  private[niffler] def getStorage: Map[Token[_], ExecutionCacheEntry[_]] = storage.toMap

  def getValues: Map[Token[_], Any] = storage.mapValues(_.result).toMap

  def omit(tokens: Set[Token[_]]): ExecutionCache = {
    new ExecutionCache(getStorage.filterKeys(p => !tokens.contains(p)))
  }

  def invalidateTtlCache(now: Long): Unit = {
    storage.retain({
      case (_, value) =>
        value.ttl.isEmpty || now < value.stats.completeTime + value.ttl.get
    })
  }

  def fork: ExecutionCache = {
    ExecutionCache(getStorage)
  }

  def store[T](token: Token[T], value: T, stats: TokenEvaluationStats, ttl: Option[Long]): Unit = {
    storage(token) = ExecutionCacheEntry(value, stats, ttl)
  }

  def evict[T](token: Token[T]): Unit = {
    storage.remove(token)
  }
}
