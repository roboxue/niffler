package com.roboxue.niffler

import org.jgrapht.GraphPath
import org.jgrapht.alg.shortestpath.AllDirectedPaths
import org.jgrapht.graph.DefaultEdge

import scala.collection.JavaConversions._

/**
  * @author rxue
  * @since 12/19/17.
  */
case class NifflerEvaluationException(logic: Logic,
                                      keyToEvaluate: Key[_],
                                      keyWithException: Key[_],
                                      stats: KeyEvaluationStats,
                                      exception: Throwable)
    extends Exception {
  def getPaths: Seq[GraphPath[Key[_], DefaultEdge]] = {
    new AllDirectedPaths(logic.topology).getAllPaths(keyWithException, keyToEvaluate, true, null).toSeq
  }
}
