package com.roboxue.niffler.scalaDSL
import com.roboxue.niffler.{Token, TokenMeta}

/**
  * @author robert.xue
  * @since 7/15/18
  */
trait Formula[T] {
  val dependsOn: Seq[Token[_]]
  val outlet: Token[T]
}
