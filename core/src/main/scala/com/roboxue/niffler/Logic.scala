package com.roboxue.niffler

import com.roboxue.niffler.execution.AsyncExecution
import org.jgrapht.Graphs
import org.jgrapht.graph.{DefaultEdge, DirectedAcyclicGraph}
import org.jgrapht.traverse.TopologicalOrderIterator

import scala.collection.JavaConverters._

/**
  * @author robert.xue
  * @since 7/15/18
  */
class Logic(flows: Iterable[DataFlow[_]]) {
  val dag: DirectedAcyclicGraph[Token[_], DefaultEdge] = {
    val g = new DirectedAcyclicGraph[Token[_], DefaultEdge](classOf[DefaultEdge])
    for (flow <- flows) {
      Graphs.addIncomingEdges(g, flow.outlet, flow.dependsOn.asJava)
    }
    g
  }

  lazy val flowReference: Map[Token[_], DataFlow[_]] = flows.map(f => f.outlet -> f).toMap

  def diverge(extraFlow: Iterable[DataFlow[_]]): Logic = new Logic(flows ++ extraFlow)

  def asyncRun[T](token: Token[T],
                  extraFlow: Iterable[DataFlow[_]] = Iterable.empty,
                  state: ExecutionState = ExecutionState.empty): AsyncExecution[T] = {
    AsyncExecution(diverge(extraFlow), state, token, AsyncExecution.executionId.incrementAndGet())
  }

  def printFlowChart(logger: String => Unit): Unit = {
    val it: TopologicalOrderIterator[Token[_], DefaultEdge] = new TopologicalOrderIterator(dag)
    logger("graph TD;")
    for (t <- dag.vertexSet().asScala) {
      logger(s"""${t.uuid.replaceAll("-", "")}["${t.name}:${t.typeDescription}"]""")
    }
    for (t <- it.asScala) {
      for (d <- Graphs.predecessorListOf(dag, t).asScala) {
        logger(s"${t.uuid.replaceAll("-", "")} --> ${d.uuid.replaceAll("-", "")};")
      }
    }
  }
}
