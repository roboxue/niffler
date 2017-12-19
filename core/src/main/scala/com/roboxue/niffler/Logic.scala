package com.roboxue.niffler

import org.jgrapht.Graphs
import org.jgrapht.graph.{DefaultEdge, DirectedAcyclicGraph}

import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer

/**
  * @author rxue
  * @since 12/15/17.
  */
class Logic(private[niffler] val topology: DirectedAcyclicGraph[Key[_], DefaultEdge],
            bindings: Map[Key[_], ImplementationDetails[_]]) {
  def getDependents(key: Key[_]): Set[Key[_]] = {
    Graphs.predecessorListOf(topology, key).toSet
  }

  def getParents(key: Key[_]): Set[Key[_]] = {
    Graphs.successorListOf(topology, key).toSet
  }

  def getUnmetDependencies(key: Key[_], executionCache: ExecutionCache): Set[Key[_]] = {
    var unmet = Set[Key[_]]()
    var keysToInspect: Set[Key[_]] = Set(key)
    do {
      var nextInspect = ListBuffer.empty[Key[_]]
      for (k <- keysToInspect if executionCache.miss(k)) {
        unmet += k
        nextInspect ++= getDependents(k)
      }
      keysToInspect = nextInspect.distinct.toSet
    } while (keysToInspect.nonEmpty)
    unmet
  }

  def dependencyMet(key: Key[_], executionCache: ExecutionCache): Boolean = {
    getDependents(key).forall(executionCache.hit)
  }

  def implForKey[T](key: Key[T]): Implementation[T] =
    Implementation(key, bindings(key).asInstanceOf[ImplementationDetails[T]])

  def asyncRun[T](key: Key[T], cache: ExecutionCache = ExecutionCache.empty): AsyncExecution[T] = {
    AsyncExecution(this, key, cache)
  }

  def syncRun[T](key: Key[T], cache: ExecutionCache = ExecutionCache.empty): ExecutionResult[T] = {
    asyncRun(key, cache).await
  }

}

object Logic {
  def apply(binding: Seq[Implementation[_]]): Logic = {
    val topology = new DirectedAcyclicGraph[Key[_], DefaultEdge](classOf[DefaultEdge])
    val bindingMap: Map[Key[_], ImplementationDetails[_]] = binding.map(r => r.key -> r.implementationDetails).toMap

    for ((key, impl) <- bindingMap) {
      Graphs.addIncomingEdges(topology, key, impl.dependency)
    }

    new Logic(topology, bindingMap)
  }
}
