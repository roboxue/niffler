package com.roboxue.niffler

import akka.actor.ActorSystem
import com.roboxue.niffler.execution.CachingPolicy
import monix.eval.Coeval

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
  * @author rxue
  * @since 12/24/17.
  */
trait Niffler {
  private lazy val nifflerName: String = getClass.getName.stripSuffix("$")
  private[niffler] val implementations = ListBuffer.empty[Coeval[Implementation[_]]]
  private[niffler] val cachingPolicies = mutable.Map.empty[Token[_], CachingPolicy]

  def getLogic: Logic = {
    Logic(Niffler.getGlobalImpls ++ collectImplementations, cachingPolicies.toMap, nifflerName)
  }

  def importFrom(anotherNiffler: Niffler): Unit = {
    implementations ++= anotherNiffler.implementations
    cachingPolicies ++= anotherNiffler.cachingPolicies
  }

  protected def addImpl(impl: => Implementation[_]): Unit = {
    implementations += Coeval(impl)
  }

  protected def $$(impl: => Implementation[_]): Unit = {
    addImpl(impl)
  }

  protected def updateCachingPolicy(token: Token[_], cachingPolicy: CachingPolicy): Unit = {
    cachingPolicies(token) = cachingPolicy
  }

  private def collectImplementations: Iterable[Implementation[_]] = {
    implementations.toList.map(_.apply())
  }

}

object Niffler {
  // global tokens
  final val argv: Token[Array[String]] = Token("commandline arguments from main function")

  def init(args: Array[String], existingActorSystem: Option[ActorSystem] = None): Unit = {
    actorSystem = existingActorSystem
    addGlobalImpl(argv.assign(args))
  }

  def terminate(shutdownAkkaSystem: Boolean = true): Unit = {
    if (shutdownAkkaSystem) {
      actorSystem.foreach(_.terminate())
    }
    actorSystem = None
    globalImplsMap.clear()
  }

  def addGlobalImpl(impl: Implementation[_]): Unit = {
    globalImplsMap(impl.token) = impl
  }

  def getGlobalImpls: Iterable[Implementation[_]] = {
    globalImplsMap.values
  }

  // internal global state
  private var actorSystem: Option[ActorSystem] = None
  private val globalImplsMap: mutable.Map[Token[_], Implementation[_]] = mutable.Map.empty

  protected[niffler] def getActorSystem: ActorSystem = {
    if (actorSystem.isEmpty) {
      actorSystem = Some(ActorSystem("niffler"))
    }
    actorSystem.get
  }

  // type class
  implicit def nifflerIsLogic(niffler: Niffler): Logic = {
    niffler.getLogic
  }
}
