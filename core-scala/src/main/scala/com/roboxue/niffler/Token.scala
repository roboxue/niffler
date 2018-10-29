package com.roboxue.niffler

import org.json4s.JsonAST._
import org.json4s.JsonDSL._

import scala.reflect.runtime.universe.TypeTag
import scala.reflect.runtime.universe.typeOf

case class Token[T: TypeTag](codeName: String, uuid: String, summaryName: String, description: String) {
  type T0 = T
  val typeName: String = typeOf[T].toString

  def toJValue(v: T): JValue = {
    //TODO: to be replaced with Protobuf
    typeName.toLowerCase match {
      case "string" => JString(v.asInstanceOf[String])
      case "int" => JInt(v.asInstanceOf[Int])
      case "long" => JLong(v.asInstanceOf[Long])
      case "double" => JDouble(v.asInstanceOf[Double])
      case "float" => JDouble(v.asInstanceOf[Float])
      case "boolean" => JBool(v.asInstanceOf[Boolean])
      case "string_list" => v.asInstanceOf[Seq[String]]
      case "int_list" => v.asInstanceOf[Seq[Int]]
      case "long_list" => v.asInstanceOf[Seq[Long]]
      case "double_list" => v.asInstanceOf[Seq[Double]]
      case "float_list" => v.asInstanceOf[Seq[Float]]
      case "boolean_list" => v.asInstanceOf[Seq[Boolean]]
    }
  }

}
