package com.roboxue.niffler

import com.roboxue.niffler.execution.{CachingPolicy, NifflerEvaluationException, NifflerTimeoutException}
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
class Logic(private[niffler] val topology: DirectedAcyclicGraph[Token[_], DefaultEdge],
            bindings: Map[Token[_], DirectImplementation[_]],
            cachingPolicies: Map[Token[_], CachingPolicy]) {

  /**
    * Try execute the token in async mode
    *
    * @param token the token to evaluate
    * @param cache the cache used to speed up the evaluation
    * @return async execution result that contains a promise
    */
  def asyncRun[T](token: Token[T], cache: ExecutionCache = ExecutionCache.empty): AsyncExecution[T] = {
    AsyncExecution(this, token, cache)
  }

  /**
    * Try execute the token in sync mode (blocking the main thread until execution finished
    *
    * @param token   the token to evaluate
    * @param cache   the cache used to speed up the evaluation
    * @param timeout max timeout that we can wait
    * @return execution result
    * @throws NifflerEvaluationException when execution encounters failure
    * @throws NifflerTimeoutException    when execution times out
    */
  def syncRun[T](token: Token[T],
                 cache: ExecutionCache = ExecutionCache.empty,
                 timeout: Duration = Duration.Inf): ExecutionResult[T] = {
    asyncRun(token, cache).await(timeout)
  }

  def getDependents(token: Token[_]): Set[Token[_]] = {
    Graphs.predecessorListOf(topology, token).toSet
  }

  def getParents(token: Token[_]): Set[Token[_]] = {
    Graphs.successorListOf(topology, token).toSet
  }

  def getUnmetDependencies(token: Token[_], executionCache: ExecutionCache): Set[Token[_]] = {
    var unmet = Set[Token[_]]()
    var tokensToInspect: Set[Token[_]] = Set(token)
    do {
      var nextInspect = ListBuffer.empty[Token[_]]
      for (k <- tokensToInspect if executionCache.miss(k)) {
        unmet += k
        nextInspect ++= getDependents(k)
      }
      tokensToInspect = nextInspect.distinct.toSet
    } while (tokensToInspect.nonEmpty)
    unmet
  }

  def implForToken[T](token: Token[T]): DirectImplementation[T] =
    bindings(token).asInstanceOf[DirectImplementation[T]]

  def tokensInvolved: Set[Token[_]] = topology.vertexSet().toSet

  def cachingPolicy(token: Token[_]): CachingPolicy = cachingPolicies.getOrElse(token, CachingPolicy.Forever)

  private[niffler] def allDependenciesMet(token: Token[_], executionCache: ExecutionCache): Boolean = {
    getDependents(token).forall(executionCache.hit)
  }

}

object Logic {

  /**
    *
    * @param binding         implementations contained in the logic
    * @param cachingPolicies override [[CachingPolicy]] for each token. Default is [[CachingPolicy.Forever]]
    * @throws IllegalArgumentException if there is a self-reference cycle found in the logic
    * @throws NoSuchElementException   if an implementation is missing
    * @return
    */
  def apply(binding: Seq[Implementation[_]], cachingPolicies: Map[Token[_], CachingPolicy] = Map.empty): Logic = {
    val topology = new DirectedAcyclicGraph[Token[_], DefaultEdge](classOf[DefaultEdge])
    val finalBindingMap: mutable.Map[Token[_], DirectImplementation[_]] = mutable.Map.empty
    for (Implementation(token, sketch) <- binding) {
      sketch match {
        case d: DirectImplementation[_] =>
          finalBindingMap(token) = d
        case i: IncrementalImplementation[_] =>
          if (finalBindingMap.contains(token)) {
            finalBindingMap(token) = i
              .asInstanceOf[IncrementalImplementation[token.R0]]
              .merge(finalBindingMap(token).asInstanceOf[DirectImplementation[token.R0]])
          } else {
            throw new NoSuchElementException(
              s"no implementation has been provided for token ${token.debugString} prior to it depends on itself"
            )
          }
      }
    }
    for ((token, impl) <- finalBindingMap) {
      Graphs.addIncomingEdges(topology, token, impl.dependency)
    }

    new Logic(topology, finalBindingMap.toMap, cachingPolicies)
  }
}
