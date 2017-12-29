package com.roboxue.niffler.monitoring

import org.jgrapht.Graphs
import org.jgrapht.graph.DirectedAcyclicGraph

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
  * @author rxue
  * @since 12/28/17.
  */
object TopologyVertexRanker {
  def apply[V, E](graph: DirectedAcyclicGraph[V, E], startFrom: V): Seq[Set[V]] = {
    import scala.collection.JavaConversions._
    if (!graph.containsVertex(startFrom)) {
      throw new IllegalArgumentException(s"vertex $startFrom doesn't belong to graph $graph")
    }
    val visited = mutable.Set.empty[V]
    val storage = ListBuffer.empty[Set[V]]
    var toVisit = Set(startFrom)
    var i = 1
    while (toVisit.nonEmpty) {
      val visitNext = mutable.Set.empty[V]
      storage += toVisit
      for (v <- toVisit) {
        visited += v
        visitNext ++= Graphs.predecessorListOf(graph, v)
      }
      toVisit = visitNext.diff(visited).toSet
      i += 1
    }
    storage.toList
  }
}
