package com.roboxue.niffler.execution

import com.roboxue.niffler.{ExecutionCache, Token}
import shapeless.Id
import shapeless.PolyDefns.~>

import scala.util.DynamicVariable

/**
  * @author rxue
  * @since 12/22/17.
  */
object CacheFetcher extends (Token ~> Id) {
  val cacheBinding: DynamicVariable[ExecutionCache] = new DynamicVariable(ExecutionCache.empty)

  override def apply[T](token: Token[T]): Id[T] = {
    cacheBinding.value(token)
  }
}
