package com.roboxue.niffler.execution

import com.roboxue.niffler.Token

/**
  * @author rxue
  * @since 12/22/17.
  */
case class NifflerInvocationException(snapshot: ExecutionSnapshot, tokensMissingImpl: Set[Token[_]]) extends Exception {
  override def getMessage: String = {
    s"missing impl for ${tokensMissingImpl.size} tokens: ${tokensMissingImpl.map(_.debugString).mkString("[", ",", "]")}"
  }

  override def toString: String = {
    s"""${getClass.getName} when evaluating token ${snapshot.tokenToEvaluate.debugString}
       |Missing implementation for tokens in Logic ${snapshot.logic.name}
       |${tokensMissingImpl.map(t => s" -\t${t.debugString}").mkString(System.lineSeparator())}""".stripMargin
  }
}
