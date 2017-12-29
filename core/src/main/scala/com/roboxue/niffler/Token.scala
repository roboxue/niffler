package com.roboxue.niffler

import java.util.UUID

import com.roboxue.niffler.syntax.TokenSyntax
import scala.reflect.runtime.universe.TypeTag

/**
  * @author rxue
  * @since 12/15/17.
  */
class Token[T: TypeTag](val name: String, val uuid: String, val codeName: String) extends TokenSyntax[T] {

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

  override def equals(obj: scala.Any): Boolean = {
    obj.isInstanceOf[Token[T]] && obj.asInstanceOf[Token[T]].uuid == uuid
  }

  override def hashCode(): Int = {
    uuid.hashCode
  }

}

object Token {
  def apply[T: TypeTag](name: String)(implicit _codeName: sourcecode.Name): Token[T] = {
    new Token[T](name, UUID.randomUUID().toString, _codeName.value)
  }
}
