package com.roboxue.niffler

import com.roboxue.niffler.execution.CachingPolicy
import monix.eval.Coeval

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
  * @author rxue
  * @since 12/24/17.
  */
trait Niffler {
  private[niffler] val implementations = ListBuffer.empty[Coeval[Implementation[_]]]
  private[niffler] val cachingPolicies = mutable.Map.empty[Token[_], CachingPolicy]
  protected implicit val niffler: Niffler = this

  def getLogic: Logic = {
    Logic(collectImplementations, cachingPolicies.toMap)
  }

  def importFrom(anotherNiffler: Niffler): Unit = {
    implementations ++= anotherNiffler.implementations
    cachingPolicies ++= anotherNiffler.cachingPolicies
  }

  protected[niffler] def addImpl(impl: => Implementation[_]): Unit = {
    implementations += Coeval(impl)
  }

  protected def updateCachingPolicy(token: Token[_], cachingPolicy: CachingPolicy): Unit = {
    cachingPolicies(token) = cachingPolicy
  }

  private def collectImplementations: Iterable[Implementation[_]] = {
    implementations.toList.map(_.apply())
  }

}

object Niffler {
  implicit def nifflerIsLogic(niffler: Niffler): Logic = {
    niffler.getLogic
  }
}
