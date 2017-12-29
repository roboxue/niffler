package com.roboxue.niffler.execution

import scala.concurrent.duration.FiniteDuration

/**
  * @author rxue
  * @since 12/19/17.
  */
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
