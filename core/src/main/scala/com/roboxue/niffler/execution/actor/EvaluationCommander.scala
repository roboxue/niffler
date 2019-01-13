package com.roboxue.niffler.execution.actor

import akka.actor.{Actor, ActorLogging, PoisonPill}
import com.roboxue.niffler.execution._
import com.roboxue.niffler.execution.actor.EvaluationCommander.{
  EvaluationCancelled,
  EvaluationFailure,
  EvaluationSuccess
}
import com.roboxue.niffler.{MutableExecutionState, Token}
import org.jgrapht.Graphs
import org.jgrapht.graph.{DefaultEdge, DirectedAcyclicGraph}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * @author robert.xue
  * @since 7/15/18
  */
class EvaluationCommander[T](execution: AsyncExecution[T]) extends Actor with ActorLogging {

  val logs: ListBuffer[ExecutionLogEntry] = ListBuffer.empty
  val unseen: mutable.Queue[Token[_]] = mutable.Queue.empty
  val seen: mutable.Set[Token[_]] = mutable.Set.empty
  val dag: DirectedAcyclicGraph[Token[_], DefaultEdge] = execution.logic.dag
  val state: MutableExecutionState = execution.initialState.mutableCopy
  val blockers: mutable.Map[Token[_], Set[Token[_]]] = mutable.Map.empty
  val evaluating: mutable.Set[Token[_]] = mutable.Set.empty
  @volatile var failed: Boolean = false
  unseen.enqueue(execution.token)

  logIt(LogStarted(execution.executionId))
  while (unseen.nonEmpty) {
    analyze(unseen.dequeue())
  }

  def analyze(token: Token[_]): Unit = {
    if (seen.contains(token)) {
      return
    }
    seen += token
    val unseenCache = ListBuffer.empty[Token[_]]
    if (dag.containsVertex(token)) {
      val dep = Graphs.predecessorListOf(dag, token).asScala.toSet
      unseenCache ++= dep
      val met = dep.intersect(state.keySet)
      val unmet = dep.diff(state.keySet)
      logIt(TokenAnalyzed(token, unmet, met))
      if (unmet.isEmpty) {
        logIt(TokenStartedEvaluation(token))
        evaluating += token
        trigger(token)
      } else {
        for (blocker <- unmet) {
          blockers(blocker) = blockers.getOrElse(blocker, Set.empty) + token
        }
        logIt(TokenBacklogged(token, unmet))
      }
    } else {
      val ex = NifflerNoDataFlowDefinedException(token)
      logIt(TokenFailedEvaluation(token, ex))
      enterFailureProtocol(token, ex)
    }
    unseen.enqueue(unseenCache.toSet.diff(seen).toSeq: _*)
  }

  def enterFailureProtocol(cause: Token[_], ex: Throwable): Unit = {
    failed = true
    for (token <- evaluating) {
      logIt(TokenCancelledEvaluation(token, Some(cause)))
    }
    logIt(LogEnded())
    self ! PoisonPill
    execution.failure(NifflerDataFlowExecutionException(cause, ex))
  }

  def enterCancellationProtocol(): Unit = {
    failed = true
    for (token <- evaluating) {
      logIt(TokenCancelledEvaluation(token, None))
    }
    logIt(LogEnded())
    self ! PoisonPill
    execution.failure(NifflerCancelledException)
  }

  def enterSuccessProtocol(result: T): Unit = {
    execution.success(
      ExecutionResult(result, state.seal, ExecutionLog(execution.executionId, logs), execution.executionId)
    )
    logIt(LogEnded())
    self ! PoisonPill
  }

  def revisit(token: Token[_], blockerRemoved: Token[_]): Unit = {
    val dep = Graphs.predecessorListOf(dag, token).asScala.toSet
    val met = dep.intersect(state.keySet)
    val unmet = dep.diff(state.keySet)
    logIt(TokenRevisited(token, blockerRemoved, unmet, met))
    if (unmet.isEmpty) {
      logIt(TokenStartedEvaluation(token))
      evaluating += token
      trigger(token)
    }
  }

  def trigger(token: Token[_]): Unit = {
    execution.logic.flowReference.get(token) match {
      case Some(flow) if flow.length == 1 =>
        flow.head
          .evaluate(state)(ExecutionContext.global)
          .onComplete({
            case Success(result) =>
              self ! EvaluationSuccess(token, result)
            case Failure(ex) =>
              self ! EvaluationFailure(token, ex)
          })(context.dispatcher)
      case Some(flows) =>
        flows
          .drop(1)
          .foldLeft[Future[_]](flows.head.evaluate(state)(ExecutionContext.global))({ (f, flow) =>
            implicit val ec = ExecutionContext.global
            f.flatMap(r => {
              state.put(token.asInstanceOf[Token[token.T0]], r.asInstanceOf[token.T0])
              flow.evaluate(state)
            })
          })
          .onComplete({
            case Success(result) =>
              self ! EvaluationSuccess(token, result)
            case Failure(ex) =>
              self ! EvaluationFailure(token, ex)
          })(context.dispatcher)
      case None =>
        val ex = NifflerNoDataFlowDefinedException(token)
        logIt(TokenFailedEvaluation(token, ex))
        enterFailureProtocol(token, ex)
    }
  }

  override def receive: Receive = {
    case EvaluationSuccess(token, result) =>
      logIt(TokenEndedEvaluation(token))
      evaluating -= token
      state.put(token.asInstanceOf[Token[token.T0]], result.asInstanceOf[token.T0])
      if (token == execution.token) {
        enterSuccessProtocol(result.asInstanceOf[T])
      } else {
        for (blockedTokens <- blockers.remove(token);
             blocked <- blockedTokens) {
          revisit(blocked, token)
        }
      }
    case EvaluationFailure(token, ex) =>
      logIt(TokenFailedEvaluation(token, ex))
      evaluating -= token
      enterFailureProtocol(token, ex)
    case EvaluationCancelled =>
      enterCancellationProtocol()
  }

  def logIt(entry: ExecutionLogEntry): Unit = {
    logs += entry
  }
}

object EvaluationCommander {
  case class EvaluationSuccess(token: Token[_], result: Any)
  case class EvaluationFailure(token: Token[_], ex: Throwable)
  case object EvaluationCancelled
}
