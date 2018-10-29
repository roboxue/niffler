package com.roboxue.niffler

import org.json4s.JsonAST.JValue

case class TokenValuePair[T](token: Token[T], value: T) {
  def valueToJValue: JValue = token.toJValue(value)
}
