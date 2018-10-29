package com.roboxue.niffler

import scala.reflect.runtime.universe.TypeTag
import scala.reflect.runtime.universe.typeOf

case class Token[T : TypeTag](codeName: String, uuid: String, summaryName: String, description: String) {
  type T0 = T
  val typeName: String = typeOf[T].toString
}
