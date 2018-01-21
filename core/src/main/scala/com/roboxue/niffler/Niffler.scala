package com.roboxue.niffler

import akka.actor.ActorSystem
import com.google.common.collect.EvictingQueue
import com.roboxue.niffler.execution.CachingPolicy
import com.roboxue.niffler.syntax.Constant
import monix.eval.Coeval

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
  * @author rxue
  * @since 12/24/17.
  */
trait Niffler {
  private lazy val nifflerName: String = getClass.getName.stripSuffix("$")
  private[niffler] val operations = ListBuffer.empty[Coeval[DataFlowOperation[_]]]
  private[niffler] val cachingPolicies = mutable.Map.empty[Token[_], CachingPolicy]

  def getLogic: Logic = {
    Logic(NifflerRuntime.getGlobalOperations ++ collectImplementations, cachingPolicies.toMap, nifflerName)
  }

  protected def addOperation(op: => DataFlowOperation[_]): Unit = {
    operations += Coeval(op)
  }

  protected def $$(impl: => DataFlowOperation[_]): Unit = {
    addOperation(impl)
  }

  protected def updateCachingPolicy(token: Token[_], cachingPolicy: CachingPolicy): Unit = {
    cachingPolicies(token) = cachingPolicy
  }

  private def collectImplementations: Iterable[DataFlowOperation[_]] = {
    operations.toList.map(_.apply())
  }

}

object Niffler {
  // global tokens
  final val argv: Token[Array[String]] = Token("commandline arguments from main function")

  def combine(nifflers: Niffler*): Logic = {
    val n = new MutableNiffler
    for (niffler <- nifflers) {
      n.importFrom(niffler)
    }
    n.getLogic
  }

  // type class
  implicit def nifflerIsLogic(niffler: Niffler): Logic = {
    niffler.getLogic
  }
}

private class MutableNiffler {
  private val implementations: ListBuffer[Coeval[DataFlowOperation[_]]] = ListBuffer.empty[Coeval[DataFlowOperation[_]]]
  private val cachingPolicies: mutable.Map[Token[_], CachingPolicy] = mutable.Map.empty
  private val nifflerNames: ListBuffer[String] = ListBuffer.empty

  def importFrom(anotherNiffler: Niffler): Unit = {
    nifflerNames += anotherNiffler.name
    implementations ++= anotherNiffler.operations
    cachingPolicies ++= anotherNiffler.cachingPolicies
  }

  def nifflerName: String = {
    nifflerNames.mkString("Niffler with ", " with ", "")
  }

  def getLogic: Logic = {
    Logic(
      NifflerRuntime.getGlobalOperations ++ implementations.toList.map(_.apply()),
      cachingPolicies.toMap,
      nifflerName
    )
  }
}
