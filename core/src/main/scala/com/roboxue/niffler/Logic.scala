package com.roboxue.niffler

import java.util.UUID

import com.roboxue.niffler.execution.{CachingPolicy, NifflerEvaluationException, NifflerTimeoutException}
import org.jgrapht.Graphs
import org.jgrapht.graph.{DefaultEdge, DirectedAcyclicGraph}

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.concurrent.duration.Duration

/**
  * @author rxue
  * @since 12/15/17.
  */
class Logic private (val name: String,
                     bindings: Map[Token[_], DirectImplementation[_]],
                     cachingPolicies: Map[Token[_], CachingPolicy]) {
  private[niffler] val topology: DirectedAcyclicGraph[Token[_], DefaultEdge] = {
    val g = new DirectedAcyclicGraph[Token[_], DefaultEdge](classOf[DefaultEdge])
    for ((token, impl) <- bindings) {
      Graphs.addIncomingEdges(g, token, impl.dependency)
    }
    g
  }

  /**
    * Try execute the token in async mode
    *
    * @param token the token to evaluate
    * @param extraImpl extra implementations applied to this execution. useful when providing input
    * @param cache the cache used to speed up the evaluation
    * @return async execution result that contains a promise
    */
  def asyncRun[T](token: Token[T],
                  extraImpl: Iterable[Implementation[_]] = Iterable.empty,
                  cache: ExecutionCache = ExecutionCache.empty): AsyncExecution[T] = {
    AsyncExecution(this, token, cache)
  }

  /**
    * Try execute the token in sync mode (blocking the main thread until execution finished
    *
    * @param token the token to evaluate
    * @param extraImpl extra implementations applied to this execution. useful when providing input
    * @param cache the cache used to speed up the evaluation
    * @param timeout max timeout that we can wait
    * @return execution result
    * @throws NifflerEvaluationException when execution encounters failure
    * @throws NifflerTimeoutException    when execution times out
    */
  def syncRun[T](token: Token[T],
                 extraImpl: Iterable[Implementation[_]] = Iterable.empty,
                 cache: ExecutionCache = ExecutionCache.empty,
                 timeout: Duration = Duration.Inf): ExecutionResult[T] = {
    asyncRun(token, extraImpl, cache).await(timeout)
  }

  def getDependents(token: Token[_]): Set[Token[_]] = {
    Graphs.predecessorListOf(topology, token).toSet
  }

  def getParents(token: Token[_]): Set[Token[_]] = {
    Graphs.successorListOf(topology, token).toSet
  }

  /**
    * A breadth first search for unmet dependencies in the dependency chain of the given token
    * If a token exists in the cache, its predecessor will not be checked since there is no need to evaluate them
    *
    * @param token the token to be evaluated
    * @param executionCache cache containing tokens that doesn't need evaluation
    * @return
    */
  def getUnmetDependencies(token: Token[_], executionCache: ExecutionCache): Set[Token[_]] = {
    // the token to be evaluated will automatically become "unmet"
    var unmet = Set[Token[_]](token)
    var tokensToInspect: Set[Token[_]] = Set(token)
    do {
      var nextInspect = mutable.Set.empty[Token[_]]
      for (k <- tokensToInspect if executionCache.miss(k)) {
        // add every involved token who is missing from cache
        unmet += k
        // inspect this token's predecessors
        nextInspect ++= getDependents(k)
      }
      tokensToInspect = nextInspect.toSet
    } while (tokensToInspect.nonEmpty)
    unmet
  }

  def implForToken[T](token: Token[T]): DirectImplementation[T] =
    bindings(token).asInstanceOf[DirectImplementation[T]]

  def tokensInvolved: Set[Token[_]] = topology.vertexSet().toSet

  def cachingPolicy(token: Token[_]): CachingPolicy = cachingPolicies.getOrElse(token, CachingPolicy.Forever)

  def checkMissingImpl(cache: ExecutionCache, forToken: Token[_]): Set[Token[_]] = {
    getUnmetDependencies(forToken, cache).diff(bindings.keySet)
  }

  private[niffler] def allDependenciesMet(token: Token[_], executionCache: ExecutionCache): Boolean = {
    getDependents(token).forall(executionCache.hit)
  }

}

object Logic {

  /**
    *
    * @param name a friendly name for this logic used in debug outputs. default to an UUID
    * @param binding implementations contained in the logic
    * @param cachingPolicies override [[CachingPolicy]] for each token. Default is [[CachingPolicy.Forever]]
    * @throws IllegalArgumentException if there is a self-reference cycle found in the logic
    * @throws NoSuchElementException   if an implementation is missing
    * @return
    */
  def apply(binding: Iterable[Implementation[_]],
            cachingPolicies: Map[Token[_], CachingPolicy] = Map.empty,
            name: String = s"logic-${UUID.randomUUID()}"): Logic = {
    val finalBindingMap: mutable.Map[Token[_], DirectImplementation[_]] = mutable.Map.empty
    for (impl <- binding) {
      impl match {
        case d: DirectImplementation[_] =>
          finalBindingMap(impl.token) = d
        case i: IncrementalImplementation[_, _] =>
          finalBindingMap(impl.token) =
            i.merge(finalBindingMap.get(i.token).map(_.asInstanceOf[DirectImplementation[i.token.T0]]))
      }
    }

    new Logic(name, finalBindingMap.toMap, cachingPolicies)
  }
}
