package com.roboxue.niffler.server

import java.util.concurrent.atomic.AtomicLong

import akka.actor.{Actor, ActorRef}
import com.google.common.graph.ImmutableGraph
import com.roboxue.niffler.server.SessionActor.{RequestAbort, RequestEvaluation}
import com.roboxue.niffler.{NifflerSession, Token, TokenValuePair}

import scala.collection.JavaConverters._
import scala.collection.mutable

class SessionActor(seeds: Seq[TokenValuePair[_]],
                   dependencyChain: Map[Token[_], Seq[Token[_]]],
                   toEvaluate: Token[_],
                   retryLimit: Int,
                   coordinator: ActorRef) extends Actor {
  assert(dependencyChain.contains(toEvaluate))
  val graph: ImmutableGraph[Token[_]] = Execution.buildGraph(dependencyChain)
  val retryCounter: mutable.Map[Token[_], Int] = mutable.Map.empty

  val session = NifflerSession(SessionActor.sessionIdGenerator.incrementAndGet(), System.currentTimeMillis())
  val updateRecord: mutable.Map[Token[_], Long] = mutable.Map()

  for (TokenValuePair(t, v) <- seeds) {
    session.set(t, v)
    updateRecord(t) = session.startTime
  }

  def createContext(t: Token[_]): Seq[TokenValuePair[_]] = {
    graph.predecessors(t).asScala.map(d => {
      TokenValuePair(d.asInstanceOf[Token[d.T0]], session.get(d).asInstanceOf[d.T0])
    }).toSeq
  }

  val dependenciesToEvaluate: Seq[Token[_]] = Execution.figureOutAllPossibleEvaluations(graph, toEvaluate, session)
  if (dependenciesToEvaluate.isEmpty) {
    coordinator ! RequestEvaluation(session.sessionId, toEvaluate, createContext(toEvaluate), System.currentTimeMillis())
  } else {
    for (d <- dependenciesToEvaluate) {
      coordinator ! RequestEvaluation(session.sessionId, d, createContext(d), System.currentTimeMillis())
    }
  }

  override def receive: Receive = {
    case SessionActor.SessionUpdate(session.sessionId, tvp, updateAt) =>
      session.set(tvp.token, tvp.value.asInstanceOf[tvp.token.T0])
      updateRecord(tvp.token) = updateAt
      for (eligible <- graph.successors(tvp.token).asScala.filter(d => Execution.checkUnmetDependencies(graph, d, session).isEmpty)) {
        coordinator ! RequestEvaluation(session.sessionId, eligible, createContext(eligible), System.currentTimeMillis())
      }
    case SessionActor.SessionFailure(session.sessionId, token, reason, updatedAt) =>
      retryCounter(token) = retryCounter.getOrElse(token, 0) + 1
      if (retryCounter(token) > retryLimit) {
        coordinator ! RequestAbort(session.sessionId, System.currentTimeMillis())
      } else {
        coordinator ! RequestEvaluation(session.sessionId, token, createContext(token), System.currentTimeMillis(),
          retry = retryCounter(token))
      }
  }
}

object SessionActor {
  val sessionIdGenerator = new AtomicLong()

  case class SessionUpdate(sessionId: Long, tvp: TokenValuePair[_], updateAt: Long)

  case class SessionFailure(sessionId: Long, token: Token[_], failureReason: String, updateAt: Long)

  case class RequestEvaluation(sessionId: Long, token: Token[_], context: Seq[TokenValuePair[_]], requestedAt: Long, retry: Int = 0)

  case class RequestAbort(sessionId: Long, requestedAt: Long)
}