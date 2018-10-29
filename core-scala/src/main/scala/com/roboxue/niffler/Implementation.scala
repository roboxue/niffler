package com.roboxue.niffler

case class Implementation[T](fulfill: Token[T], dependencies: Seq[Token[_]], implementation: NifflerSession => T)
