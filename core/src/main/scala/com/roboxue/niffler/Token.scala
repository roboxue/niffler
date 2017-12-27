package com.roboxue.niffler

import java.util.UUID

import com.roboxue.niffler.syntax.TokenSyntax
import scala.reflect.runtime.universe.TypeTag

/**
  * @author rxue
  * @since 12/15/17.
  */
case class Token[T: TypeTag](name: String, uuid: String = UUID.randomUUID().toString) extends TokenSyntax[T] {

  /**
    * Used by external to reference [[T]]
    */
  type T0 = T

  lazy val returnTypeDescription: String = {
    import scala.reflect.runtime.universe._
    typeOf[T].toString
  }

  override def toString: String = s"$name[$returnTypeDescription]"

  def debugString: String = s"'$name'[$returnTypeDescription]($uuid)"

  override def canEqual(that: Any): Boolean = {
    that.isInstanceOf[Token[T]]
  }

  override def equals(obj: scala.Any): Boolean = {
    canEqual(obj) && obj.asInstanceOf[Token[T]].uuid == uuid
  }

  override def hashCode(): Int = {
    uuid.hashCode
  }

}
