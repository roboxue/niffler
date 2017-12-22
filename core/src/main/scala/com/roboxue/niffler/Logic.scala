package com.roboxue.niffler

import org.jgrapht.Graphs
import org.jgrapht.graph.{DefaultEdge, DirectedAcyclicGraph}

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.Duration

/**
  * @author rxue
  * @since 12/15/17.
  */
class Logic(private[niffler] val topology: DirectedAcyclicGraph[Key[_], DefaultEdge],
            bindings: Map[Key[_], ImplementationDetails[_]],
            cachingPolicies: Map[Key[_], CachingPolicy]) {

  /**
    * Try execute the key in async mode
    *
    * @param key   the key to evaluate
    * @param cache the cache used to speed up the evaluation
    * @return async execution result that contains a promise
    */
  def asyncRun[T](key: Key[T], cache: ExecutionCache = ExecutionCache.empty): AsyncExecution[T] = {
    AsyncExecution(this, key, cache)
  }

  /**
    * Try execute the key in sync mode (blocking the main thread until execution finished
    *
    * @param key     the key to evaluate
    * @param cache   the cache used to speed up the evaluation
    * @param timeout max timeout that we can wait
    * @return execution result
    * @throws NifflerEvaluationException when execution encounters failure
    * @throws NifflerTimeoutException    when execution times out
    */
  def syncRun[T](key: Key[T],
                 cache: ExecutionCache = ExecutionCache.empty,
                 timeout: Duration = Duration.Inf): ExecutionResult[T] = {
    asyncRun(key, cache).await(timeout)
  }

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

  def implForKey[T](key: Key[T]): ImplementationDetails[T] = bindings(key).asInstanceOf[ImplementationDetails[T]]

  def keys: Set[Key[_]] = topology.vertexSet().toSet

  def cachingPolicy(key: Key[_]): CachingPolicy = cachingPolicies.getOrElse(key, CachingPolicy.Forever)

  private[niffler] def allDependenciesMet(key: Key[_], executionCache: ExecutionCache): Boolean = {
    getDependents(key).forall(executionCache.hit)
  }

}

object Logic {

  /**
    *
    * @param binding implementations contained in the logic
    * @param cachingPolicies override [[CachingPolicy]] for each key. Default is [[CachingPolicy.Forever]]
    * @throws IllegalArgumentException if there is a self-reference cycle found in the logic
    * @throws NoSuchElementException if an implementation is missing
    * @return
    */
  def apply(binding: Seq[Implementation[_]], cachingPolicies: Map[Key[_], CachingPolicy] = Map.empty): Logic = {
    val topology = new DirectedAcyclicGraph[Key[_], DefaultEdge](classOf[DefaultEdge])
    val finalBindingMap: mutable.Map[Key[_], ImplementationDetails[_]] = mutable.Map.empty
    for (Implementation(key, sketch) <- binding) {
      sketch match {
        case d: ImplementationDetails[_] =>
          finalBindingMap(key) = d
        case i: ImplementationIncrement[_] =>
          if (finalBindingMap.contains(key)) {
            finalBindingMap(key) = i
              .asInstanceOf[ImplementationIncrement[key.R0]]
              .merge(finalBindingMap(key).asInstanceOf[ImplementationDetails[key.R0]])
          } else {
            throw new NoSuchElementException(
              s"no implementation has been provided for key ${key.debugString} prior to it depends on itself"
            )
          }
      }
    }
    for ((key, impl) <- finalBindingMap) {
      Graphs.addIncomingEdges(topology, key, impl.dependency)
    }

    new Logic(topology, finalBindingMap.toMap, cachingPolicies)
  }
}
