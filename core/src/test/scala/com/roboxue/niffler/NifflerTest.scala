package com.roboxue.niffler

import org.scalatest.{FlatSpec, Matchers}

/**
  * @author rxue
  * @since 12/24/17.
  */
class NifflerTest extends FlatSpec with Matchers {
  it should "add impl properly" in {
    trait Test1 extends Niffler {
      val token1: Token[Int] = Token[Int]("t1")
      val token2: Token[Int] = Token[Int]("t2")
      def getToken2Impl: DataFlowOperation[Int]
      addLogicPart(getToken2Impl)
    }
    object Test2 extends Test1 {
      override def getToken2Impl: DataFlowOperation[Int] = {
        token2 := token1.mapFormula(_ + 3)
      }

    }
    val logic = Test2.getLogic
    logic.tokensInvolved shouldBe Set(Test2.token1, Test2.token2)
    logic.checkMissingImpl(ExecutionCache.empty, Test2.token2) shouldBe Set(Test2.token1)
  }
}
