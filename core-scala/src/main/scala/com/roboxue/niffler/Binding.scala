package com.roboxue.niffler

case class Binding[T](token: Token[T], impl: Implementation[T])
