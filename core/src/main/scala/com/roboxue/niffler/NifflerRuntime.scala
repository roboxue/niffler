package com.roboxue.niffler

import akka.actor.ActorSystem
import com.google.common.collect.EvictingQueue
import com.roboxue.niffler.Niffler.argv
import com.roboxue.niffler.syntax.Constant
import monix.execution.atomic.AtomicInt

import scala.collection.mutable

/**
  * @author rxue
  * @since 1/20/18.
  */
object NifflerRuntime {
  // internal global state
  private val executionId: AtomicInt = AtomicInt(0)
  private var actorSystem: Option[ActorSystem] = None
  private val globalOperationsMap: mutable.Map[Token[_], DataFlowOperation[_]] = mutable.Map.empty
  private val liveExecutions: mutable.Set[AsyncExecution[_]] = mutable.Set.empty
  private var executionHistory: EvictingQueue[AsyncExecution[_]] = EvictingQueue.create(20)

  // life cycle
  def init(args: Array[String],
           executionHistoryLimit: Int = 20,
           existingActorSystem: Option[ActorSystem] = None): Unit = {
    executionHistory = EvictingQueue.create(executionHistoryLimit)
    actorSystem = existingActorSystem
    addGlobalOperation(argv := Constant(args))
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
    globalOperationsMap.clear()
  }

  // global ops
  def addGlobalOperation(impl: DataFlowOperation[_]): Unit = {
    globalOperationsMap(impl.token) = impl
  }

  def getGlobalOperations: Iterable[DataFlowOperation[_]] = {
    globalOperationsMap.values
  }

  // execution history
  def updateExecutionHistoryCapacity(newLimit: Int): Unit = synchronized {
    val newQueue = EvictingQueue.create[AsyncExecution[_]](newLimit)
    import scala.collection.JavaConversions._
    newQueue.addAll(executionHistory.toSeq)
    executionHistory = newQueue
  }

  def getHistory: (Seq[AsyncExecution[_]], Seq[AsyncExecution[_]], Int) = synchronized {
    (getLiveExecutions, getPastExecutions, executionHistory.remainingCapacity())
  }

  def getLiveExecutions: Seq[AsyncExecution[_]] = {
    liveExecutions.toSeq
  }

  def getPastExecutions: Seq[AsyncExecution[_]] = {
    import scala.collection.JavaConversions._
    executionHistory.toSeq
  }

  def getNewExecutionId: Int = {
    executionId.incrementAndGet()
  }

  def registerNewExecution(execution: AsyncExecution[_]): Unit = {
    liveExecutions.add(execution)
  }

  def reportExecutionComplete(execution: AsyncExecution[_]): Unit = synchronized {
    liveExecutions.remove(execution)
    executionHistory.offer(execution)
  }

  private[niffler] def getActorSystem: ActorSystem = {
    if (actorSystem.isEmpty) {
      actorSystem = Some(ActorSystem("niffler"))
    }
    actorSystem.get
  }
}
