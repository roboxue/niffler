package com.roboxue.niffler

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.testkit.{DefaultTimeout, TestKit}
import com.roboxue.niffler.execution._
import org.scalatest.{BeforeAndAfterEach, FlatSpecLike, Matchers}

import scala.concurrent.duration.Duration

/**
  * @author rxue
  * @since 12/18/17.
  */
class LogicTest
    extends TestKit(ActorSystem("NifflerTest"))
    with FlatSpecLike
    with Matchers
    with DefaultTimeout
    with BeforeAndAfterEach {

  override protected def beforeEach(): Unit = {
    Niffler.init(Array.empty, Some(system))
  }

  override protected def afterEach(): Unit = {
    Niffler.terminate(false)
  }

  it should "run with dependsOn" in {
    val k1 = Token[Int]("k1")
    val k2 = Token[Int]("k2")
    val k3 = Token[Int]("k3")
    val k4 = Token[Int]("k4")
    val k5 = Token[Int]("k5")
    val logic = Logic(Seq(k5.dependsOn(k4) { (k4) =>
      k4 + 5
    }, k4.assign(4), k3.dependsOn(k4) { k4 =>
      k4 + 3
    }, k2.dependsOn(k4) { k4 =>
      k4 + 2
    }, k1.dependsOn(k2, k3) { (k2, k3) =>
      k2 + k3 + 1
    }))

    val ExecutionResult(result, snapshot, newCache) = logic.syncRun(k1, timeout = timeout.duration)
    result shouldBe 14
    newCache.getValues shouldBe Map(k1 -> 14, k2 -> 6, k3 -> 7, k4 -> 4)
  }

  it should "run with amend" in {
    val t1: Token[String] = Token("a string")
    val t2: Token[Int] = Token("an int")
    val t3: Token[Int] = Token("another int")
    val t3Impl: Implementation[Int] = t3.dependsOn(t1) { (v1) =>
      v1.length
    }
    val t3Amend: Implementation[Int] = t3.amendWith(t2) { (v2) =>
      v2
    }
    val logic1: Logic = Logic(Seq(t1.assign("hello"), t2.assign(3), t3Impl, t3Amend))
    logic1.syncRun(t3) match {
      case ExecutionResult(result, _, cache) =>
        result shouldBe 8
        cache.getValues shouldBe Map(t1 -> "hello", t2 -> 3, t3 -> 8)
    }
    val logic2: Logic = Logic(Seq(t3Impl, t3Amend))
    logic2.syncRun(t3, cache = ExecutionCache.fromValue(Map(t1 -> "wow", t2 -> 6))) match {
      case ExecutionResult(result, _, cache) =>
        result shouldBe 9
        cache.getValues shouldBe Map(t1 -> "wow", t2 -> 6, t3 -> 9)
    }
    val logic3: Logic = Logic(Seq(t3Amend))
    logic3.syncRun(t3, cache = ExecutionCache.fromValue(Map(t1 -> "wow", t2 -> 6, t3 -> 42))) match {
      case ExecutionResult(result, _, cache) =>
        result shouldBe 48
        cache.getValues shouldBe Map(t1 -> "wow", t2 -> 6, t3 -> 48)
    }
  }

  it should "discard non cached value" in {
    val k1 = Token[Int]("k1")
    val k2 = Token[Int]("k2")
    val k3 = Token[Int]("k3")
    val k4 = Token[Int]("k4")
    val k5 = Token[Int]("k5")
    val logic = Logic(
      Seq(
        k5.dependsOn(k4) { (k4) =>
          k4 + 5
        },
        k4.assign(4),
        k3.dependsOn(k4) { k4 =>
          k4 + 3
        },
        k2.dependsOn(k4) { k4 =>
          k4 + 2
        },
        k1.dependsOn(k2, k3) { (k2, k3) =>
          // make sure k2's cache will always expire after this round of execution
          Thread.sleep(30)
          k2 + k3 + 1
        }
      ),
      Map(k1 -> CachingPolicy.WithinExecution, k2 -> CachingPolicy.Timed(Duration(1, TimeUnit.MILLISECONDS)))
    )

    val ExecutionResult(result, snapshot, newCache) = logic.syncRun(k1, timeout = timeout.duration)
    result shouldBe 14
    newCache.getValues shouldBe Map(k3 -> 7, k4 -> 4)
    snapshot.cache.getValues shouldBe Map(k1 -> 14, k2 -> 6, k3 -> 7, k4 -> 4)
  }

  it should "report runtime exception" in {
    val k1 = Token[Int]("k1")
    val k2 = Token[Int]("k2")
    val k3 = Token[Int]("k3")
    val logic = Logic(Seq(k1.assign({
      throw new Exception("hello niffler")
    }), k2.dependsOn(k1) { (k1) =>
      k1 + 1
    }, k3.dependsOn(k2) { (k2) =>
      k2 + 1
    }))

    val nifflerEx = intercept[NifflerEvaluationException] {
      logic.syncRun(k3)
    }
    nifflerEx.snapshot.tokenToEvaluate shouldBe k3
    nifflerEx.tokenWithException shouldBe k1
    nifflerEx.exception.getMessage shouldBe "hello niffler"
    nifflerEx.getPaths.length shouldBe 1
    nifflerEx.getPaths.head.getVertexList.toArray shouldBe Array(k1, k2, k3)
  }

  it should "run with amends only for a key and existing cache entry" in {
    val t2: Token[Int] = Token("an int")
    val t3: Token[Int] = Token("another int")
    val t3Amend1: Implementation[Int] = t3.amendWith(t2) { (v2) =>
      v2
    }
    val t3Amend2: Implementation[Int] = t3.amendWith(1)
    val logic3: Logic = Logic(Seq(t3Amend1, t3Amend2, t2.assign(6)))
    logic3
      .syncRun(t3, cache = ExecutionCache(Map(t3 -> ExecutionCacheEntry(42))), timeout = timeout.duration)
      .result shouldBe 49
  }

  it should "run with amends only for a key and no cache entry" in {
    val t2: Token[Int] = Token("an int")
    val t3: Token[Int] = Token("another int")
    val t3Amend1: Implementation[Int] = t3.amendWith(t2) { (v2) =>
      v2
    }
    val t3Amend2: Implementation[Int] = t3.amendWith(1)
    val logic3: Logic = Logic(Seq(t3Amend1, t3Amend2, t2.assign(6)))
    logic3.syncRun(t3, timeout = timeout.duration).result shouldBe 7
  }

  it should "fail with missing implementation" in {
    val t1: Token[String] = Token("a string")
    val t2: Token[Int] = Token("an int")
    val t3: Token[Int] = Token("another int")
    val t3Impl: Implementation[Int] = t3
      .dependsOn(t1, t2) { (t1, v2) =>
        t1.length + v2
      }
    val logic3: Logic = Logic(Seq(t3Impl))
    val ex = intercept[NifflerInvocationException] {
      logic3.syncRun(t3)
    }
    ex.tokensMissingImpl should contain only (t1, t2)
  }

  it should "report timeout exception" in {
    val k1 = Token[Int]("k1")
    val k2 = Token[Int]("k2")
    val k3 = Token[Int]("k3")
    val logic = Logic(Seq(k1.assign(1), k2.dependsOn(k1) { (k1) =>
      Thread.sleep(200)
      k1 + 1
    }, k3.dependsOn(k2) { (k2) =>
      k2 + 1
    }))

    val nifflerEx = intercept[NifflerTimeoutException] {
      logic.syncRun(k3, timeout = Duration(100, TimeUnit.MILLISECONDS))
    }
    nifflerEx.executionSnapshot.tokenToEvaluate shouldBe k3
    nifflerEx.executionSnapshot.ongoing.keySet should contain only k2
    nifflerEx.executionSnapshot.cache.tokens should contain only k1
    nifflerEx.timeout.length shouldBe 100
  }
}
