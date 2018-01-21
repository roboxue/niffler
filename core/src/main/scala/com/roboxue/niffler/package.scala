package com.roboxue

import com.roboxue.niffler.execution.{ExecutionSnapshot, NifflerExceptionBase}
import org.jgrapht.GraphPath
import org.jgrapht.alg.shortestpath.AllDirectedPaths
import org.jgrapht.graph.DefaultEdge

import scala.collection.JavaConversions._
import scala.concurrent.duration.FiniteDuration
import scala.language.existentials

/**
  * @author rxue
  * @since 1/20/18.
  */
package object niffler {

  case class NifflerCancellationException(reason: String) extends Exception(reason) {}

  case class NifflerInvocationException(override val snapshot: ExecutionSnapshot, tokensMissingImpl: Set[Token[_]])
      extends NifflerExceptionBase(snapshot) {
    override def getMessage: String = {
      s"missing impl for ${tokensMissingImpl.size} tokens: ${tokensMissingImpl.map(_.debugString).mkString("[", ",", "]")}"
    }

    override def toString: String = {
      s"""${getClass.getName} when evaluating token ${snapshot.tokenToEvaluate.debugString}
         |Missing implementation for tokens in Logic ${snapshot.logic.name}
         |${tokensMissingImpl.map(t => s" -\t${t.debugString}").mkString(System.lineSeparator())}""".stripMargin
    }
  }

  case class NifflerTimeoutException(override val snapshot: ExecutionSnapshot, timeout: FiniteDuration)
      extends NifflerExceptionBase(snapshot) {
    override def getMessage: String = {
      s"timeout exception when executing ${snapshot.tokenToEvaluate.debugString} (limit: ${timeout.toString()})"
    }

    override def toString: String = {
      s"""${getClass.getName} when evaluating token ${snapshot.tokenToEvaluate.debugString}
         |Timeout exception. Execution exceeded ${timeout.toString()}
         |Now: ${snapshot.asOfTime}
         |Unfinished token evaluations:
         |${snapshot.ongoing
           .map(t => s" -\t${t._1.debugString}: ${t._2}")
           .mkString(System.lineSeparator())}""".stripMargin
    }

  }

  case class NifflerEvaluationException(override val snapshot: ExecutionSnapshot,
                                        tokenWithException: Token[_],
                                        exception: Throwable)
      extends NifflerExceptionBase(snapshot) {
    setStackTrace(exception.getStackTrace)

    def getPaths: Seq[GraphPath[Token[_], DefaultEdge]] = {
      new AllDirectedPaths(snapshot.logic.dag)
        .getAllPaths(tokenWithException, snapshot.tokenToEvaluate, true, null)
        .toSeq
    }

    override def getMessage: String = {
      s"${exception.getClass.getSimpleName.stripSuffix("$")}: ${exception.getMessage}"
    }

    override def toString: String = {
      s"""when evaluating NifflerToken ${snapshot.tokenToEvaluate.debugString} in logic ${snapshot.logic.name}
         |Caused by ${tokenWithException.debugString}:
         |${exception.toString}""".stripMargin
    }
  }

}
