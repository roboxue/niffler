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
  private[niffler] val implementations = ListBuffer.empty[Coeval[DataFlowOperation[_]]]
  private[niffler] val cachingPolicies = mutable.Map.empty[Token[_], CachingPolicy]

  def getLogic: Logic = {
    Logic(Niffler.getGlobalLogicParts ++ collectImplementations, cachingPolicies.toMap, nifflerName)
  }

  protected def addLogicPart(impl: => DataFlowOperation[_]): Unit = {
    implementations += Coeval(impl)
  }

  protected def $$(impl: => DataFlowOperation[_]): Unit = {
    addLogicPart(impl)
  }

  protected def updateCachingPolicy(token: Token[_], cachingPolicy: CachingPolicy): Unit = {
    cachingPolicies(token) = cachingPolicy
  }

  private def collectImplementations: Iterable[DataFlowOperation[_]] = {
    implementations.toList.map(_.apply())
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

  def init(args: Array[String],
           executionHistoryLimit: Int = 20,
           existingActorSystem: Option[ActorSystem] = None): Unit = {
    executionHistory = EvictingQueue.create(executionHistoryLimit)
    actorSystem = existingActorSystem
    addGlobalLogicPart(argv := Constant(args))
  }

  def updateExecutionHistoryCapacity(newLimit: Int): Unit = synchronized {
    val newQueue = EvictingQueue.create[AsyncExecution[_]](newLimit)
    import scala.collection.JavaConversions._
    newQueue.addAll(executionHistory.toSeq)
    executionHistory = newQueue
  }

  def terminate(shutdownAkkaSystem: Boolean = true): Unit = synchronized {
    for (execution <- liveExecutions) {
      execution.requestCancellation()
    }
    if (shutdownAkkaSystem) {
      actorSystem.foreach(_.terminate())
    }
    liveExecutions.clear()
    executionHistory.clear()
    actorSystem = None
    globalLogicPartsMap.clear()
  }

  def addGlobalLogicPart(impl: DataFlowOperation[_]): Unit = {
    globalLogicPartsMap(impl.token) = impl
  }

  def getGlobalLogicParts: Iterable[DataFlowOperation[_]] = {
    globalLogicPartsMap.values
  }

  // internal global state
  private var actorSystem: Option[ActorSystem] = None
  private val globalLogicPartsMap: mutable.Map[Token[_], DataFlowOperation[_]] = mutable.Map.empty
  private val liveExecutions: mutable.Set[AsyncExecution[_]] = mutable.Set.empty
  private var executionHistory: EvictingQueue[AsyncExecution[_]] = EvictingQueue.create(20)

  private[niffler] def getHistory: (Seq[AsyncExecution[_]], Seq[AsyncExecution[_]], Int) = synchronized {
    (getLiveExecutions, getPastExecutions, executionHistory.remainingCapacity())
  }

  private[niffler] def getLiveExecutions: Seq[AsyncExecution[_]] = {
    liveExecutions.toSeq
  }

  private[niffler] def getPastExecutions: Seq[AsyncExecution[_]] = {
    import scala.collection.JavaConversions._
    executionHistory.toSeq
  }

  private[niffler] def registerNewExecution(execution: AsyncExecution[_]): Unit = {
    liveExecutions.add(execution)
  }

  private[niffler] def reportExecutionComplete(execution: AsyncExecution[_]): Unit = synchronized {
    liveExecutions.remove(execution)
    executionHistory.offer(execution)
  }

  private[niffler] def getActorSystem: ActorSystem = {
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

private class MutableNiffler {
  private val implementations: ListBuffer[Coeval[DataFlowOperation[_]]] = ListBuffer.empty[Coeval[DataFlowOperation[_]]]
  private val cachingPolicies: mutable.Map[Token[_], CachingPolicy] = mutable.Map.empty
  private val nifflerNames: ListBuffer[String] = ListBuffer.empty

  def importFrom(anotherNiffler: Niffler): Unit = {
    nifflerNames += anotherNiffler.name
    implementations ++= anotherNiffler.implementations
    cachingPolicies ++= anotherNiffler.cachingPolicies
  }

  def nifflerName: String = {
    nifflerNames.mkString("Niffler with ", " with ", "")
  }

  def getLogic: Logic = {
    Logic(Niffler.getGlobalLogicParts ++ implementations.toList.map(_.apply()), cachingPolicies.toMap, nifflerName)
  }
}
