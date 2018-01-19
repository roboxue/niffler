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

  /**
    * Create a [[Formula]] that declare dependency on this token with an extra transformation
    * @param transform the function used to yield [[R]] using the runtime value of this token with type [[T]]
    * @return a [[Formula]]
    */
  def asFormula[R](transform: T => R): Formula[R] = Requires(this)(transform)

  /**
    * Create a [[Formula]] that declare dependency on this token with no extra transformation
    * @return a [[Formula]] that will return the same value as this token holds in runtime
    */
  def asFormula: Formula[T] = asFormula[T]((i) => i)

  /**
    * Provide this [[Token]] a [[Formula]] that will inject the dependency and instructions to evaluate runtime value
    * @param formula the [[Formula]] that binds to this [[Token]]
    * @return [[RegularOperation]] assembled
    */
  def :=(formula: Formula[T]): RegularOperation[T] = {
    RegularOperation(this, formula)
  }

  /**
    * Provide this [[Token]] a [[Formula]] that will inject the dependency and instructions to increment runtime value
    * @param formula the [[Formula]] that will increment this [[Token]]'s runtime value
    * @return [[IncrementalOperation]] assembled
    */
  def +=[R](formula: Formula[R])(implicit canAmendTWithR: Append.Value[T, R]): IncrementalOperation[T, R] = {
    IncrementalOperation(this, formula, canAmendTWithR)
  }

}

object Token {
  def apply[T: TypeTag](name: String)(implicit _codeName: sourcecode.Name): Token[T] = {
    new Token[T](name, UUID.randomUUID().toString, _codeName.value)
  }
}
