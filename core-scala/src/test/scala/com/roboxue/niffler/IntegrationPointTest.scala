package com.roboxue.niffler

import org.scalatest.{FlatSpec, Matchers}

import scala.collection.JavaConverters._

class IntegrationPointTest extends FlatSpec with Matchers {
  it should "find the test object" in {
    val i1 = Class.forName("com.roboxue.niffler.IntegrationPointTest")
      .getMethod("duplicateToArrayInScala").invoke(null)
    i1.asInstanceOf[Implementation[_]].dependencies shouldBe Seq(IntegrationPointTest.times, IntegrationPointTest.word)

    val i2 = Class.forName("com.roboxue.niffler.IntegrationPointTest")
      .getMethod("duplicateToArrayInJava").invoke(null)
    i2.asInstanceOf[Implementation[_]].dependencies shouldBe Seq(IntegrationPointTest.times, IntegrationPointTest.word)

    val i3 = Class.forName("com.roboxue.niffler.IntegrationPointTest")
      .getMethod("duplicateToArrayInJava2").invoke(null)
    i3.asInstanceOf[Implementation[_]].dependencies shouldBe Seq(IntegrationPointTest.times, IntegrationPointTest.word)
  }
}

object IntegrationPointTest {
  val times: Token[Int] = Token("times", "t1uuid", "times", "number of times to be repeated")
  val word: Token[String] = Token("word", "t2uuid", "word", "the word to be repeated")

  @IntegrationPoint
  def duplicateToArrayInScala: Implementation[java.util.List[String]] = Implementation(times, word) { (v1, v2) =>
    Range(0, v1).map(_ => v2).asJava
  }

  @IntegrationPoint
  def duplicateToArrayInJava: Implementation[java.util.List[String]] = Implementation(times, word)(TestJavaIntegrationPoint.duplicateArrayJavaImpl)

  @IntegrationPoint
  def duplicateToArrayInJava2: Implementation[java.util.List[String]] = Implementation(times, word)(new TestJavaIntegrationPoint().duplicateArrayJavaImpl2)

}
