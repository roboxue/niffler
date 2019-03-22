package com.roboxue.niffler

import java.nio.file.Paths
import java.util.UUID

import com.roboxue.niffler.Append.{Value, Values}

import scala.concurrent.{ExecutionContext, Future}
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

  def stackTrace: StackTraceElement

  override def toString: String = s"$name[$typeDescription]"

  def debugString: String = s"'$name'[$typeDescription]($uuid)"

  override def equals(obj: scala.Any): Boolean = {
    obj.isInstanceOf[TokenMeta] && obj.asInstanceOf[TokenMeta].uuid == uuid
  }

  override def hashCode(): Int = {
    uuid.hashCode
  }
}

class Token[T: TypeTag](val name: String, val uuid: String, val codeName: String, val stackTrace: StackTraceElement)
    extends TokenMeta
    with TokenSyntax[T] {
  type T0 = T
  override def typeDescription: String = {
    import scala.reflect.runtime.universe._
    typeOf[T].toString
  }
}

object Token {
//  implicit def tokenIsFormula0[T](token: Token[T]): Formula0[T] = Formula0(token)

  private def stackTraceElement(implicit _className: sourcecode.Enclosing,
                                _fileName: sourcecode.File,
                                _line: sourcecode.Line): StackTraceElement = {
    val (className, methodName) = _className.value.splitAt(_className.value.lastIndexOf("."))
    new StackTraceElement(className, methodName.drop(1), Paths.get(_fileName.value).getFileName.toString, _line.value)
  }

  def apply[T: TypeTag](name: String, codeName: String)(implicit _codeName: sourcecode.Name,
                                                        _className: sourcecode.Enclosing,
                                                        _fileName: sourcecode.File,
                                                        _line: sourcecode.Line): Token[T] = {
    new Token(name, UUID.randomUUID().toString, codeName, stackTraceElement)
  }

  def apply[T: TypeTag](name: String)(implicit _codeName: sourcecode.Name,
                                      _className: sourcecode.Enclosing,
                                      _fileName: sourcecode.File,
                                      _line: sourcecode.Line): Token[T] = {
    new Token(name, UUID.randomUUID().toString, _codeName.value, stackTraceElement)
  }

  def accumulator[T: TypeTag](name: String, codeName: String)(implicit _codeName: sourcecode.Name,
                                                              _className: sourcecode.Enclosing,
                                                              _fileName: sourcecode.File,
                                                              _line: sourcecode.Line): AccumulatorToken[T] = {
    new AccumulatorToken(name, UUID.randomUUID().toString, codeName, stackTraceElement)
  }

  def accumulator[T: TypeTag](name: String)(implicit _codeName: sourcecode.Name,
                                            _className: sourcecode.Enclosing,
                                            _fileName: sourcecode.File,
                                            _line: sourcecode.Line): AccumulatorToken[T] = {
    new AccumulatorToken(name, UUID.randomUUID().toString, _codeName.value, stackTraceElement)
  }

//  def module(name: String, codeName: String): Module = {
//    new Module(name, UUID.randomUUID().toString, codeName)
//  }
//
//  def module(name: String)(implicit _codeName: sourcecode.Name): Module = {
//    new Module(name, UUID.randomUUID().toString, _codeName.value)
//  }
}

class AccumulatorToken[T: TypeTag](name: String, uuid: String, codeName: String, stackTrack: StackTraceElement)
    extends Token[T](name, uuid, codeName, stackTrack) {
  self =>

  def +=[Z](token: Token[Z])(implicit value: Value[T, Z]): DataFlow[T] = new DataFlow[T] {
    override val cleanUpPreviousDataFlow: Boolean = false
    override val dependsOn: Seq[Token[_]] = Seq(token)
    override val outlet: Token[T] = self
    override def evaluate(state: ExecutionStateLike)(implicit ex: ExecutionContext): Future[T] = {
      if (state.contains(self)) {
        Future(value.appendValue(state(self), state(token)))
      } else {
        Future(value.appendValue(value.empty, state(token)))
      }
    }
  }

  def +=[Z](impl: => Z)(implicit value: Value[T, Z]): DataFlow[T] = new DataFlow[T] {
    override val cleanUpPreviousDataFlow: Boolean = false
    override val dependsOn: Seq[Token[_]] = Seq()
    override val outlet: Token[T] = self
    override def evaluate(state: ExecutionStateLike)(implicit ex: ExecutionContext): Future[T] = {
      if (state.contains(self)) {
        Future(value.appendValue(state(self), impl))
      } else {
        Future(value.appendValue(value.empty, impl))
      }
    }
  }

  def ++=[Z](token: Token[Z])(implicit values: Values[T, Z]): DataFlow[T] = new DataFlow[T] {
    override val cleanUpPreviousDataFlow: Boolean = false
    override val dependsOn: Seq[Token[_]] = Seq(token)
    override val outlet: Token[T] = self
    override def evaluate(state: ExecutionStateLike)(implicit ex: ExecutionContext): Future[T] = {
      if (state.contains(self)) {
        Future(values.extendValues(state(self), state(token)))
      } else {
        Future(values.extendValues(values.empty, state(token)))
      }
    }
  }

  def ++=[Z](impl: => Z)(implicit values: Values[T, Z]): DataFlow[T] = new DataFlow[T] {
    override val cleanUpPreviousDataFlow: Boolean = false
    override val dependsOn: Seq[Token[_]] = Seq()
    override val outlet: Token[T] = self
    override def evaluate(state: ExecutionStateLike)(implicit ex: ExecutionContext): Future[T] = {
      if (state.contains(self)) {
        Future(values.extendValues(state(self), impl))
      } else {
        Future(values.extendValues(values.empty, impl))
      }
    }
  }
}

trait TokenSyntax[T] {
  this: Token[T] =>

  /**
    * Java friendly
    * @param value
    * @return
    */
  def initializedTo(value: T): DataFlow[T] = new SyncDataFlow[T](Seq.empty, this, _ => value)

  def :=(impl: => T): DataFlow[T] = implBy(impl)

  def implBy(impl: => T): DataFlow[T] = new SyncDataFlow[T](Seq.empty, this, _ => impl)

  def :=>(futureImpl: => Future[T]): AsyncDataFlow[T] = implByFuture(futureImpl)

  def implByFuture(futureImpl: => Future[T]): AsyncDataFlow[T] =
    new AsyncDataFlow[T](Seq.empty, this, _ => futureImpl)

  def dependsOnAllOf[Z](tokens: Token[Z]*): WildcardFormula[T, Z] = WildcardFormula(tokens, this)

  def dependsOn[T1](t1: Token[T1]): Formula1[T1, T] = Formula1[T1, T](t1, this)

  def dependsOn[T1, T2](t1: Token[T1], t2: Token[T2]): Formula2[T1, T2, T] = Formula2(t1, t2, this)

  def dependsOn[T1, T2, T3](t1: Token[T1], t2: Token[T2], t3: Token[T3]): Formula3[T1, T2, T3, T] =
    Formula3(t1, t2, t3, this)

  def dependsOn[T1, T2, T3, T4](t1: Token[T1],
                                t2: Token[T2],
                                t3: Token[T3],
                                t4: Token[T4]): Formula4[T1, T2, T3, T4, T] = Formula4(t1, t2, t3, t4, this)

  def dependsOn[T1, T2, T3, T4, T5](t1: Token[T1],
                                    t2: Token[T2],
                                    t3: Token[T3],
                                    t4: Token[T4],
                                    t5: Token[T5]): Formula5[T1, T2, T3, T4, T5, T] = Formula5(t1, t2, t3, t4, t5, this)

  def copyFrom(anotherToken: Token[T]): DataFlow[T] = dependsOn(anotherToken).implBy(r => r)

  def flowFrom(dataSource: DataSource[T]): DataFlow[T] = dataSource.writesTo(this)

  def <~(dataSource: DataSource[T]): DataFlow[T] = dataSource.writesTo(this)
}

//class Module private (name: String, uuid: String, codeName: String) extends Token[Logic](name, uuid, codeName) {
//
//  def builder: ModuleBuilder = new ModuleBuilder(this)
//}
//
//class ModuleBuilder(token: Token[Logic]) {
//  private val featureSwitches = ListBuffer[FeatureSwitch[_]]()
//
//  def featureSwitch[S](token: Token[S], cases: PartialFunction[S, Seq[DataFlow[_]]]): ModuleBuilder = {
//    featureSwitches += FeatureSwitch(token, cases)
//    this
//  }
//
//  def booleanSwitch(token: Token[Boolean],
//                    onTrue: Seq[DataFlow[_]],
//                    onFalse: Seq[DataFlow[_]] = Seq.empty): ModuleBuilder = {
//    val pf: PartialFunction[Boolean, Seq[DataFlow[_]]] = {
//      case true  => onTrue
//      case false => onFalse
//    }
//    featureSwitches += FeatureSwitch(token, pf)
//    this
//  }
//
//  def compile: DataFlow[Logic] = new DataFlow[Logic] {
//    override val dependsOn: Seq[Token[_]] = featureSwitches.map(_.token).distinct
//    override val outlet: Token[Logic] = token
//    override def evaluate(state: ExecutionStateLike)(implicit ex: ExecutionContext): Future[Logic] = {
//      Future {
//        val moduleDataFlow = featureSwitches.flatMap(s => {
//          s.cases.applyOrElse(state(s.token).asInstanceOf[s.T], _ => Seq.empty[DataFlow[_]])
//        })
//        new Logic(moduleDataFlow)
//      }
//    }
//  }
//}
//
//case class FeatureSwitch[T0](token: Token[T0], cases: PartialFunction[T0, Seq[DataFlow[_]]]) {
//  type T = T0
//}
