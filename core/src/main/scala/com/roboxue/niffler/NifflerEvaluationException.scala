package com.roboxue.niffler

import org.jgrapht.GraphPath
import org.jgrapht.alg.shortestpath.AllDirectedPaths
import org.jgrapht.graph.DefaultEdge

import scala.collection.JavaConversions._
import scala.language.existentials

/**
  * @author rxue
  * @since 12/19/17.
  */
case class NifflerEvaluationException(snapshot: ExecutionSnapshot,
                                      keyWithException: Key[_],
                                      stats: KeyEvaluationStats,
                                      exception: Throwable)
    extends Exception {
  def getPaths: Seq[GraphPath[Key[_], DefaultEdge]] = {
    new AllDirectedPaths(snapshot.logic.topology)
      .getAllPaths(keyWithException, snapshot.keyToEvaluate, true, null)
      .toSeq
  }
}
