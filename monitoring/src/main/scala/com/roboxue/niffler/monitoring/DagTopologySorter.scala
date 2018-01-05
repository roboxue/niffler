package com.roboxue.niffler.monitoring

import org.jgrapht.Graphs
import org.jgrapht.graph.DirectedAcyclicGraph

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
  * @author rxue
  * @since 12/28/17.
  */
object DagTopologySorter {
  def apply[V, E](graph: DirectedAcyclicGraph[V, E], startFrom: V): Seq[Set[V]] = {
    import scala.collection.JavaConversions._
    if (!graph.containsVertex(startFrom)) {
      throw new IllegalArgumentException(s"vertex $startFrom doesn't belong to graph $graph")
    }
    val unvisited = mutable.Set(graph.getAncestors(startFrom).toSeq: _*)
    unvisited -= startFrom
    val storage = ListBuffer(Set(startFrom))
    var toVisit = Set(Graphs.predecessorListOf(graph, startFrom).toSeq: _*)
    while (toVisit.nonEmpty) {
      val visitNext = mutable.Set.empty[V]
      val cleared = ListBuffer.empty[V]
      for (v <- toVisit) {
        if (unvisited.intersect(Graphs.successorListOf(graph, v).toSet).isEmpty) {
          cleared += v
        }
        visitNext ++= Graphs.predecessorListOf(graph, v)
      }
      unvisited --= cleared
      storage += cleared.toSet
      toVisit = visitNext.toSet.intersect(unvisited)
    }
    storage.toList
  }
}
