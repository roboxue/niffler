package com.roboxue.niffler.execution

/**
  * @author rxue
  * @since 12/19/17.
  */
case class ExecutionCacheEntry[T](result: T, entryType: ExecutionCacheEntryType, ttl: Option[Long]) {
  private[niffler] def toInherited: ExecutionCacheEntry[T] = copy(entryType = ExecutionCacheEntryType.Inherited)
}

object ExecutionCacheEntry {
  def inject[T](result: T): ExecutionCacheEntry[T] = {
    new ExecutionCacheEntry(result, ExecutionCacheEntryType.Injected, None)
  }
}
