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
                     bindings: Map[Token[_], RegularOperation[_]],
                     cachingPolicies: Map[Token[_], CachingPolicy]) {
  private[niffler] val dag: DirectedAcyclicGraph[Token[_], DefaultEdge] = {
    val g = new DirectedAcyclicGraph[Token[_], DefaultEdge](classOf[DefaultEdge])
    for ((token, impl) <- bindings) {
      Graphs.addIncomingEdges(g, token, impl.prerequisites)
    }
    g
  }

  def diverge(extraBindings: Iterable[DataFlowOperation[_]],
              extraCachingPolicies: Map[Token[_], CachingPolicy] = Map.empty,
              newName: String = s"logic-${UUID.randomUUID()}"): Logic = {
    val updatedBindings = mutable.Map(bindings.toSeq: _*)
    for (impl <- extraBindings) {
      impl match {
        case d: RegularOperation[_] =>
          updatedBindings(impl.token) = d
        case i: IncrementalOperation[_, _] =>
          updatedBindings(impl.token) =
            i.merge(updatedBindings.get(i.token).map(_.asInstanceOf[RegularOperation[i.token.T0]]))
      }
    }
    val updatedCachingPolicies = cachingPolicies ++ extraCachingPolicies
    new Logic(newName, updatedBindings.toMap, updatedCachingPolicies)

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
                  extraImpl: Iterable[DataFlowOperation[_]] = Iterable.empty,
                  cache: ExecutionCache = ExecutionCache.empty): AsyncExecution[T] = {
    AsyncExecution(this.diverge(extraImpl), token, cache)
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
                 extraImpl: Iterable[DataFlowOperation[_]] = Iterable.empty,
                 cache: ExecutionCache = ExecutionCache.empty,
                 timeout: Duration = Duration.Inf): ExecutionResult[T] = {
    asyncRun(token, extraImpl, cache).await(timeout)
  }

  def getPredecessors(token: Token[_]): Set[Token[_]] = {
    Graphs.predecessorListOf(dag, token).toSet
  }

  def getSuccessors(token: Token[_]): Set[Token[_]] = {
    Graphs.successorListOf(dag, token).toSet
  }

  /**
    * A breadth first search for unmet prerequisites in the dependency chain of the given token
    * If a token exists in the cache, its predecessor will not be checked since there is no need to evaluate them
    *
    * @param token the token to be evaluated
    * @param executionCache cache containing tokens that doesn't need evaluation
    * @return
    */
  def getUnmetPrerequisites(token: Token[_], executionCache: ExecutionCache): Set[Token[_]] = {
    // the token to be evaluated will automatically become "unmet"
    var unmet = Set[Token[_]](token)
    var tokensToInspect: Set[Token[_]] = Set(token)
    do {
      var nextInspect = mutable.Set.empty[Token[_]]
      for (k <- tokensToInspect if executionCache.miss(k)) {
        // add every involved token who is missing from cache
        unmet += k
        // inspect this token's predecessors
        nextInspect ++= getPredecessors(k)
      }
      tokensToInspect = nextInspect.toSet
    } while (tokensToInspect.nonEmpty)
    unmet
  }

  def implForToken[T](token: Token[T]): RegularOperation[T] =
    bindings(token).asInstanceOf[RegularOperation[T]]

  def tokensInvolved: Set[Token[_]] = dag.vertexSet().toSet

  def cachingPolicy(token: Token[_]): CachingPolicy = cachingPolicies.getOrElse(token, CachingPolicy.Forever)

  def checkMissingImpl(cache: ExecutionCache, forToken: Token[_]): Set[Token[_]] = {
    getUnmetPrerequisites(forToken, cache).diff(bindings.keySet)
  }

  private[niffler] def allPrerequisitesMet(token: Token[_], executionCache: ExecutionCache): Boolean = {
    getPredecessors(token).forall(executionCache.hit)
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
  def apply(binding: Iterable[DataFlowOperation[_]],
            cachingPolicies: Map[Token[_], CachingPolicy] = Map.empty,
            name: String = s"logic-${UUID.randomUUID()}"): Logic = {
    val finalBindingMap: mutable.Map[Token[_], RegularOperation[_]] = mutable.Map.empty
    for (impl <- binding) {
      impl match {
        case d: RegularOperation[_] =>
          finalBindingMap(impl.token) = d
        case i: IncrementalOperation[_, _] =>
          finalBindingMap(impl.token) =
            i.merge(finalBindingMap.get(i.token).map(_.asInstanceOf[RegularOperation[i.token.T0]]))
      }
    }

    new Logic(name, finalBindingMap.toMap, cachingPolicies)
  }
}
