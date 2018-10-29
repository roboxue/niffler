package com.roboxue.niffler

import org.scalatest.{FlatSpec, Matchers}


class ImplementationTest extends FlatSpec with Matchers {
  it should "execute" in {
    val t1 = Token[Int]("t1", "t1uuid", "t1", "t1")
    val t2 = Token[String]("t2", "t2uuid", "t2", "t2")
    val impl = Implementation(t1, t2) { (v1, v2) =>
      Range(0, v1).map(_ => v2)
    }

    val session = new NifflerSession()
    session.set(t1, 5)
    session.set(t2, "a")
    impl.implementation(session) shouldBe Seq("a", "a", "a", "a", "a")
  }
}
