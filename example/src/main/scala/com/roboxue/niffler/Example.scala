package com.roboxue.niffler

import akka.actor.ActorSystem
import com.roboxue.niffler.execution.AsyncExecution

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
  * @author robert.xue
  * @since 7/15/18
  */
object Example extends App {
  val t1: Token[Int] = Token[Int]("t1")
  val t2: Token[String] = Token[String]("t2")
  val t3: Token[Boolean] = Token[Boolean]("t3")

  val c1: DataFlow[Boolean] = Requires(t1, t2) ~> t3 := { (t1, t2) =>
    t1 == t2.length
  }

  val c2: DataFlow[Boolean] = Requires(t1, t2) outputTo t3 implBy { (t1, t2) =>
    t1 == t2.length
  }
  val d1: DataFlow[Boolean] = t3.asFormula := true
  val d2: DataFlow[Boolean] = t3.asFormula implBy true

  val g1: DataFlow[Boolean] = Requires(t1, t2) implBy { (t1, t2) =>
    t1 == t2.length
  } writesTo t3
  val g2: DataFlow[Boolean] = (Requires(t1, t2) := { (t1, t2) =>
    t1 == t2.length
  }) ~> t3
  val h1: DataFlow[Boolean] = t3 flowFrom (Requires(t1, t2) implBy { (t1, t2) =>
    t1 == t2.length
  })
  val h2: DataFlow[Boolean] = t3 <~ (Requires(t1, t2) implBy { (t1, t2) =>
    t1 == t2.length
  })

  val t4: Token[Int] = Token("t4")
  val t5: Token[Seq[String]] = Token("t5")
  val t6: Token[String] = Token("t6")
  val t7: Token[String] = Token("t7")
  val t8: Token[String] = Token("t8")
  val t9: Token[String] = Token("t9")

  val execution1: AsyncExecution[String] =
    new Logic(Seq(t9.dependsOn(t8, t7) := (_ + _), t8.dependsOn(t7, t6) := (_ + _), t7.dependsOn(t5, t6) := {
      (t5, t6) =>
        t5.mkString(t6)
    }, t6.asFormula := ",", t5.asFormula := Seq("foo", "bar", "fab", "tas"))).asyncRun(t9)

  val system: ActorSystem = ActorSystem.create("example")
  execution1.withAkka(system)
  val r = Await.result(execution1.resultPromise.future, Duration.Inf)
  println(r.value)

}
