package com.roboxue.niffler

import java.util.logging.{Level, Logger}

import com.roboxue.niffler.execution.{AsyncExecution, ExecutionLogger}
import org.jgrapht.Graphs
import org.jgrapht.graph.{DefaultEdge, DirectedAcyclicGraph}
import org.jgrapht.traverse.TopologicalOrderIterator

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
  * @author robert.xue
  * @since 7/15/18
  */
class Logic(flows: Iterable[DataFlow[_]]) {
  val dag: DirectedAcyclicGraph[Token[_], DefaultEdge] = {
    val g = new DirectedAcyclicGraph[Token[_], DefaultEdge](classOf[DefaultEdge])
    for ((token, dataFlows) <- flowReference; flow <- dataFlows) {
      Graphs.addIncomingEdges(g, token, flow.dependsOn.asJava)
    }
    g
  }

  lazy val flowReference: Map[Token[_], Seq[DataFlow[_]]] = {
    val consolidation = mutable.Map[Token[_], ListBuffer[DataFlow[_]]]()
    for (flow <- flows) {
      if (flow.cleanUpPreviousDataFlow && consolidation.contains(flow.outlet)) {
        consolidation(flow.outlet).clear()
      }
      if (!consolidation.contains(flow.outlet)) {
        consolidation(flow.outlet) = ListBuffer.empty
      }
      consolidation(flow.outlet) += flow
    }
    consolidation.toMap
  }

  def diverge(extraFlow: Iterable[DataFlow[_]]): Logic = new Logic(flows ++ extraFlow)

  def asyncRun[T](token: Token[T], extraFlow: Iterable[DataFlow[_]] = Iterable.empty,
                  logger: Option[ExecutionLogger] = None)(
    implicit sc: ExecutionStateTracker = new ExecutionStateTracker
  ): AsyncExecution[T] = {
    AsyncExecution(
      diverge(extraFlow),
      sc.getExecutionState,
      token,
      AsyncExecution.executionId.incrementAndGet(),
      logger,
      Some(sc)
    )
  }

  def printFlowChart(logger: Logger, level: Level, useCodeName: Boolean): Unit = {
    val it: TopologicalOrderIterator[Token[_], DefaultEdge] = new TopologicalOrderIterator(dag)
    logger.log(level, "graph TD;")
    for (t <- dag.vertexSet().asScala) {
      if (useCodeName) {
        logger.log(level, s"""${t.uuid.replaceAll("-", "")}["${t.codeName}"]""")
      } else {
        logger.log(level, s"""${t.uuid.replaceAll("-", "")}["${t.name}:${t.typeDescription}"]""")
      }
    }
    for (t <- it.asScala) {
      for (d <- Graphs.predecessorListOf(dag, t).asScala) {
        logger.log(level, s"${t.uuid.replaceAll("-", "")} --> ${d.uuid.replaceAll("-", "")};")
      }
    }
  }
}
