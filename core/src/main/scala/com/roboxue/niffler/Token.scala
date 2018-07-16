package com.roboxue.niffler

import java.util.UUID

import scala.reflect.runtime.universe.TypeTag

/**
  * @author robert.xue
  * @since 12/15/17.
  */
trait TokenMeta {
  def name: String

  def uuid: String

  def codeName: String

  def typeDescription: String

  override def toString: String = s"$name[$typeDescription]"

  def debugString: String = s"'$name'[$typeDescription]($uuid)"

  override def equals(obj: scala.Any): Boolean = {
    obj.isInstanceOf[TokenMeta] && obj.asInstanceOf[TokenMeta].uuid == uuid
  }

  override def hashCode(): Int = {
    uuid.hashCode
  }
}

class Token[T: TypeTag](val name: String, val uuid: String, val codeName: String)
    extends TokenMeta
    with TokenSyntax[T] {
  type T0 = T
  override def typeDescription: String = {
    import scala.reflect.runtime.universe._
    typeOf[T].toString
  }
}

object Token {
  def apply[T: TypeTag](name: String)(implicit _codeName: sourcecode.Name): Token[T] = {
    new Token(name, UUID.randomUUID().toString, _codeName.value)
  }
}

trait TokenSyntax[T] {
  this: Token[T] =>
  def asFormula: Formula0[T] = Formula0(this)

  def dependsOn[T1](t1: Token[T1]): Formula1[T1, T] = Formula1[T1, T](t1, this)

  def dependsOn[T1, T2](t1: Token[T1], t2: Token[T2]): Formula2[T1, T2, T] = Formula2(t1, t2, this)

  def flowFrom(dataSource: DataSource[T]): DataFlow[T] = dataSource.writesTo(this)

  def <~(dataSource: DataSource[T]): DataFlow[T] = dataSource.writesTo(this)
}
