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
    val visited = mutable.Set(startFrom)
    val storage = ListBuffer(Set(startFrom))
    var toVisit = Set(Graphs.predecessorListOf(graph, startFrom).toSeq: _*)
    while (toVisit.nonEmpty) {
      val visitNext = mutable.Set.empty[V]
      val cleared = ListBuffer.empty[V]
      for (v <- toVisit) {
        if (visited.containsAll(Graphs.successorListOf(graph, v))) {
          cleared += v
        }
        visitNext ++= Graphs.predecessorListOf(graph, v)
      }
      visited ++= cleared
      storage += cleared.toSet
      toVisit = visitNext.diff(visited).toSet
    }
    storage.toList
  }
}
