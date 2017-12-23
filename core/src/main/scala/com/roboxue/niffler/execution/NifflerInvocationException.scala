package com.roboxue.niffler.execution

import com.roboxue.niffler.Token

/**
  * @author rxue
  * @since 12/22/17.
  */
case class NifflerInvocationException(executionSnapshot: ExecutionSnapshot, tokensMissingImpl: Set[Token[_]])
    extends Exception
