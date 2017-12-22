package com.roboxue.niffler

import java.util.UUID

import com.roboxue.niffler.syntax.{CompoundSyntax, DependencySyntax}

import scala.reflect.runtime.universe.TypeTag

/**
  * @author rxue
  * @since 12/15/17.
  */
case class Token[R: TypeTag](name: String, uuid: String = UUID.randomUUID().toString)
    extends CompoundSyntax[R]
    with DependencySyntax[R] {

  /**
    * Used by external to reference [[R]]
    */
  type R0 = R

  lazy val returnTypeDescription: String = {
    import scala.reflect.runtime.universe._
    typeOf[R].toString
  }

  override def toString: String = s"$name[$returnTypeDescription]"

  def debugString: String = s"$name[$returnTypeDescription]($uuid)"

  override def canEqual(that: Any): Boolean = {
    that.isInstanceOf[Token[R]]
  }

  override def equals(obj: scala.Any): Boolean = {
    canEqual(obj) && obj.asInstanceOf[Token[R]].uuid == uuid
  }

  override def hashCode(): Int = {
    uuid.hashCode
  }

}
