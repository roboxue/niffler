package com.roboxue.niffler

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.jgrapht.alg.shortestpath.AllDirectedPaths
import org.scalatest.{FlatSpecLike, Matchers}

/**
  * @author rxue
  * @since 12/18/17.
  */
class LogicTest extends TestKit(ActorSystem("NifflerTest")) with FlatSpecLike with Matchers {
  AsyncExecution.setActorSystem(system)

  it should "run" in {
    val k1 = Key[Int]("k1")
    val k2 = Key[Int]("k2")
    val k3 = Key[Int]("k3")
    val k4 = Key[Int]("k4")
    val k5 = Key[Int]("k5")
    val logic = Logic(Seq(k5.dependsOn(k4) { (k4: Int) =>
      k4 + 5
    }, k4.assign(4), k3.dependsOn(k4) { k4 =>
      k4 + 3
    }, k2.dependsOn(k4) { k4 =>
      k4 + 2
    }, k1.dependsOn(k2, k3) { (k2, k3) =>
      k2 + k3 + 1
    }))

    val ExecutionResult(result, _, cache) = logic.syncRun(k1)
    result shouldBe 14
    cache.getValues shouldBe Map(k1 -> 14, k2 -> 6, k3 -> 7, k4 -> 4)
  }

  it should "report exception" in {
    val k1 = Key[Int]("k1")
    val k2 = Key[Int]("k2")
    val k3 = Key[Int]("k3")
    val logic = Logic(Seq(k1.assign({
      throw new Exception("hello niffler")
    }), k2.dependsOn(k1) { k1 =>
      k1 + 1
    }, k3.dependsOn(k2) { (k2) =>
      k2 + 1
    }))

    val nifflerEx = intercept[NifflerEvaluationException] {
      logic.syncRun(k3)
    }
    nifflerEx.keyToEvaluate shouldBe k3
    nifflerEx.keyWithException shouldBe k1
    nifflerEx.exception.getMessage shouldBe "hello niffler"
    nifflerEx.getPaths.length shouldBe 1
    nifflerEx.getPaths.head.getVertexList.toArray shouldBe Array(k1, k2, k3)
  }
}
