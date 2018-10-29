package com.roboxue.niffler.server

import java.util.concurrent.atomic.AtomicLong

import akka.actor.{Actor, ActorRef}

import scala.collection.JavaConverters._
import com.roboxue.niffler.server.SessionActor.{RequestAbort, RequestEvaluation}
import com.roboxue.niffler.{NifflerSession, Token, TokenValuePair}
import com.google.common.graph.{GraphBuilder, ImmutableGraph, MutableGraph}

import scala.collection.mutable

class SessionActor(seeds: Seq[TokenValuePair[_]],
                   dependencyChain: Map[Token[_], Seq[Token[_]]],
                   toEvaluate: Token[_],
                   retryLimit: Int,
                   coordinator: ActorRef) extends Actor {
  assert(dependencyChain.contains(toEvaluate))
  val graph: ImmutableGraph[Token[_]] = SessionActor.buildGraph(dependencyChain)
  val retryCounter: mutable.Map[Token[_], Int] = mutable.Map.empty

  val session = NifflerSession(SessionActor.sessionIdGenerator.incrementAndGet(), System.currentTimeMillis())
  val updateRecord: mutable.Map[Token[_], Long] = mutable.Map()

  for (TokenValuePair(t, v) <- seeds) {
    session.set(t, v)
    updateRecord(t) = session.startTime
  }

  def checkUnmetDependencies(t: Token[_]): Seq[Token[_]] = {
    graph.predecessors(t).asScala.filter(d => !session.contains(d)).toSeq
  }

  def createContext(t: Token[_]): Seq[TokenValuePair[_]] = {
    graph.predecessors(t).asScala.map(d => {
      TokenValuePair(d.asInstanceOf[Token[d.T0]], session.get(d).asInstanceOf[d.T0])
    }).toSeq
  }

  def figureOutAllPossibleEvaluations(t: Token[_]): Seq[Token[_]] = {
    val unseen = mutable.Queue[Token[_]](checkUnmetDependencies(t): _*)
    val seen = mutable.Set.empty[Token[_]]
    val met = mutable.Set.empty[Token[_]]
    val unmet = mutable.Set.empty[Token[_]]
    if (unseen.isEmpty) {
      Seq.empty
    } else {
      while (unseen.nonEmpty) {
        val d = unseen.dequeue()
        seen += d
        val unmetForD = checkUnmetDependencies(d)
        if (unmetForD.isEmpty) {
          met += d
        } else {
          unmet += d
          unseen ++= unmetForD.filter(m => seen.contains(m))
        }
      }
      if (met.isEmpty) {
        throw new Exception("Graph cannot be computed")
      }
      met.toSeq
    }
  }

  val dependenciesToEvaluate: Seq[Token[_]] = figureOutAllPossibleEvaluations(toEvaluate)
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
      for (eligible <- graph.successors(tvp.token).asScala.filter(d => checkUnmetDependencies(d).isEmpty)) {
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

  def buildGraph(dependencyChain: Map[Token[_], Seq[Token[_]]]): ImmutableGraph[Token[_]] = {
    val graph: MutableGraph[Token[_]] = GraphBuilder.directed.build[Token[_]]
    for ((t, deps) <- dependencyChain;
         d <- deps) {
      graph.putEdge(d, t)
    }
    ImmutableGraph.copyOf(graph)
  }
}