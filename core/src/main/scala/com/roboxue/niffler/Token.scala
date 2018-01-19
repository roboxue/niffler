package com.roboxue.niffler

import java.util.UUID

import com.roboxue.niffler.execution.Append
import com.roboxue.niffler.syntax.Requires

import scala.reflect.runtime.universe.TypeTag

/**
  * @author rxue
  * @since 12/15/17.
  */
class Token[T: TypeTag](val name: String, val uuid: String, val codeName: String) {

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

  def asFormula[R](f: T => R): Formula[R] = Requires(this)(f)

  def asFormula: Formula[T] = asFormula[T]((i) => i)

  def :=(formula: Formula[T]): RegularOperation[T] = {
    RegularOperation(this, formula)
  }

  def +=[R](formula: Formula[R])(implicit canAmendTWithR: Append.Value[T, R]): IncrementalOperation[T, R] = {
    IncrementalOperation(this, formula, canAmendTWithR)
  }

}

object Token {
  def apply[T: TypeTag](name: String)(implicit _codeName: sourcecode.Name): Token[T] = {
    new Token[T](name, UUID.randomUUID().toString, _codeName.value)
  }
}
