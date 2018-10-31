package com.roboxue.niffler.server

import com.google.common.graph.{GraphBuilder, ImmutableGraph, MutableGraph}
import com.roboxue.niffler.{NifflerSession, Token}
import scala.collection.JavaConverters._

import scala.collection.mutable

object Execution {
  /**
    * Build a dependency graph from a dependency declaration
    *
    * @param dependencyChain dependency declarations
    * @return dependency graph
    */
  def buildGraph(dependencyChain: Map[Token[_], Seq[Token[_]]]): ImmutableGraph[Token[_]] = {
    val graph: MutableGraph[Token[_]] = GraphBuilder.directed.build[Token[_]]
    for ((t, deps) <- dependencyChain;
         d <- deps) {
      graph.putEdge(d, t)
    }
    ImmutableGraph.copyOf(graph)
  }

  /**
    * Look for unmet direct dependencies
    *
    * @param graph   dependency graph
    * @param token   the token to check for
    * @param session contains already evaluated tokens
    * @return all unmet direct dependencies
    */
  def checkUnmetDependencies(graph: ImmutableGraph[Token[_]], token: Token[_], session: NifflerSession): Seq[Token[_]] = {
    graph.predecessors(token).asScala.filter(d => !session.contains(d)).toSeq
  }

  /**
    * Find out all the involved tokens that can be evaluated
    *
    * @param graph           the dependency graph
    * @param tokenToEvaluate the token to be evaluated
    * @param session         contains already evaluated tokens
    * @return empty if all dependencies has been met, or all dependency that is not met but ready to be evaluated
    */
  def figureOutAllPossibleEvaluations(graph: ImmutableGraph[Token[_]], tokenToEvaluate: Token[_], session: NifflerSession): Seq[Token[_]] = {
    val unseen = mutable.Queue[Token[_]](checkUnmetDependencies(graph, tokenToEvaluate, session): _*)
    val seen = mutable.Set.empty[Token[_]]
    val met = mutable.Set.empty[Token[_]]
    val unmet = mutable.Set.empty[Token[_]]
    if (unseen.isEmpty) {
      Seq.empty
    } else {
      while (unseen.nonEmpty) {
        val tokenToBeChecked = unseen.dequeue()
        checkUnmetDependencies(graph, tokenToBeChecked, session) match {
          case n if n.isEmpty =>
            met += tokenToBeChecked
          case unmetDependencies =>
            unmet += tokenToBeChecked
            unseen ++= unmetDependencies.toSet -- seen
        }
        seen += tokenToBeChecked
      }
      if (met.isEmpty) {
        throw new Exception("Graph cannot be computed")
      }
      met.toSeq
    }
  }

}
