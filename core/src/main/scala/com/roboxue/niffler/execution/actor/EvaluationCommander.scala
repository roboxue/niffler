package com.roboxue.niffler.execution.actor

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import com.roboxue.niffler.{MutableExecutionState, Token}
import com.roboxue.niffler.execution._
import com.roboxue.niffler.execution.actor.EvaluationCommander.{
  EvaluationCancelled,
  EvaluationFailure,
  EvaluationSuccess
}
import org.jgrapht.Graphs
import org.jgrapht.graph.{DefaultEdge, DirectedAcyclicGraph}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext
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
      val ex = NifflerNoDataFlowDefinedException
      logIt(TokenFailedEvaluation(token, ex))
      enterFailureProtocol(Some(token), ex)
    }
    unseen.enqueue(unseenCache.toSet.diff(seen).toSeq: _*)
  }

  def enterFailureProtocol(cause: Option[Token[_]], ex: Throwable): Unit = {
    failed = true
    for (token <- evaluating) {
      logIt(TokenCancelledEvaluation(token, cause))
    }
    logIt(LogEnded())
    self ! PoisonPill
    execution.resultPromise.failure(ex)
  }

  def enterSuccessProtocol(result: T): Unit = {
    execution.resultPromise.success(ExecutionResult(result, state, ExecutionLog(logs), execution.executionId))
    logIt(LogEnded())
    self ! PoisonPill
  }

  def revisit(token: Token[_]): Unit = {
    val dep = Graphs.predecessorListOf(dag, token).asScala.toSet
    val unmet = dep.diff(state.keySet)
    if (unmet.isEmpty) {
      logIt(TokenStartedEvaluation(token))
      evaluating += token
      trigger(token)
    } else {
      logIt(TokenBacklogged(token, unmet))
    }
  }

  def trigger(token: Token[_]): Unit = {
    execution.logic.flowReference.get(token) match {
      case Some(flow) =>
        flow
          .evaluate(state)(ExecutionContext.global)
          .onComplete({
            case _ if failed =>
            // do nothing
            case Success(result) =>
              self ! EvaluationSuccess(token, result)
            case Failure(ex) =>
              self ! EvaluationFailure(token, ex)
          })(context.dispatcher)
      case None =>
        val ex = NifflerNoDataFlowDefinedException
        logIt(TokenFailedEvaluation(token, ex))
        enterFailureProtocol(Some(token), ex)
    }
  }

  override def receive: Receive = {
    case EvaluationSuccess(token, result) =>
      logIt(TokenEndedEvaluation(token))
      evaluating -= token
      state(token.asInstanceOf[Token[token.T0]]) = result.asInstanceOf[token.T0]
      if (token == execution.token) {
        enterSuccessProtocol(result.asInstanceOf[T])
      } else {
        for (blockedTokens <- blockers.remove(token);
             blocked <- blockedTokens) {
          revisit(blocked)
        }
      }
    case EvaluationFailure(token, ex) =>
      logIt(TokenFailedEvaluation(token, ex))
      evaluating -= token
      enterFailureProtocol(Some(token), ex)
    case EvaluationCancelled =>
      enterFailureProtocol(None, NifflerCancelledException)
  }

  def logIt(entry: ExecutionLogEntry): Unit = {
    entry match {
      case l: LogStarted =>
        log.info(s"${l.nanoTime} LogStarted")
      case l: TokenAnalyzed =>
        log.info(s"${l.nanoTime} TokenAnalyzed ${l.token.codeName}, unmet: ${l.unmet.size}, met: ${l.met.size}")
      case l: TokenBacklogged =>
        log.info(s"${l.nanoTime} TokenBacklogged ${l.token.codeName}, unmet: ${l.unmet.size}")
      case l: TokenStartedEvaluation =>
        log.info(s"${l.nanoTime} TokenStartedEvaluation ${l.token.codeName}")
      case l: TokenEndedEvaluation =>
        log.info(s"${l.nanoTime} TokenEndedEvaluation ${l.token.codeName}")
      case l: TokenFailedEvaluation =>
        log.info(s"${l.nanoTime} TokenFailedEvaluation ${l.token.codeName}")
      case l: TokenCancelledEvaluation =>
        log.info(
          s"${l.nanoTime} TokenCancelledEvaluation ${l.token.codeName} because of ${l.canceledBecause.map(_.codeName).getOrElse("external reason")}"
        )
      case l: LogEnded =>
        log.info(s"${l.nanoTime} LogEnded")
    }
    logs += entry
  }
}

object EvaluationCommander {
  case class EvaluationSuccess(token: Token[_], result: Any)
  case class EvaluationFailure(token: Token[_], ex: Throwable)
  case object EvaluationCancelled
}
