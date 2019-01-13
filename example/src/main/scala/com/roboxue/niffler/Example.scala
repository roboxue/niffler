package com.roboxue.niffler

import akka.actor.ActorSystem
import com.roboxue.niffler.SyntaxExample.logic
import com.roboxue.niffler.execution._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
  * @author robert.xue
  * @since 7/15/18
  */
object Example {
  val stringArray: Token[Seq[String]] = Token("an array of string to join")
  val separator: Token[String] = Token("char separator")
  val joinedString: Token[String] = Token("the string joined by separator")
  val palindromeLeft: Token[String] = Token("left part of the palindrome")
  val palindrome: Token[String] = Token("palindrome")

  def main(args: Array[String]): Unit = {
    val logic: Logic = new Logic(
      Seq(
        palindrome.dependsOn(palindromeLeft, joinedString) := (_ + _.reverse),
        palindromeLeft.dependsOn(joinedString, separator) := (_ + _),
        joinedString.dependsOn(stringArray, separator) := { (t5, t6) =>
          t5.mkString(t6)
        },
        separator := ",",
        stringArray := Seq("foo", "bar", "fab", "tas")
      )
    )
    // logic self documents
    logic.printFlowChart(println)
    println("========================")

    val execution1: AsyncExecution[String] = logic.asyncRun(palindrome)

    val system: ActorSystem = ActorSystem.create("example")
    val r = execution1.withAkka(system).await(Duration.Inf)
    println(r.value)
    // auto collect metrics
    println("========================")
    r.executionLog.printWaterfall(println)
    println("========================")
    r.executionLog.printFlowChart(println)

    system.terminate()
  }
}

object SyntaxExample {
  // Token as meta data & type information holder
  val t1: Token[Int] = Token[Int]("t1")
  val t2: Token[String] = Token[String]("t2")
  val t3: Token[Boolean] = Token[Boolean]("t3")

  // ExecutionState as the runtime state management solution
  val state = new MutableExecutionState(Map.empty)
  state.put(t1, 1)
  val t1Result: Int = state(t1)
  state.put(t2, "ssss")
  val t2Result: String = state(t2)

  // DataFlow as stateless computations
  val c1: DataFlow[Boolean] = Requires(t1, t2) ~> t3 := { (t1, t2) =>
    t1 == t2.length
  }

  val c2: DataFlow[Boolean] = Requires(t1, t2) outputTo t3 implBy { (t1, t2) =>
    t1 == t2.length
  }
  val d1: DataFlow[Boolean] = t3 := true
  val d2: DataFlow[Boolean] = t3 implBy true

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

  import com.roboxue.niffler.Example._

  // logic as a collection of data flow
  val logic: Logic = new Logic(
    Seq(
      palindrome.dependsOn(palindromeLeft, joinedString) := (_ + _.reverse),
      palindromeLeft.dependsOn(joinedString, separator) := (_ + _),
      joinedString.dependsOn(stringArray, separator) := { (t5, t6) =>
        t5.mkString(t6)
      },
      separator := ",",
      stringArray := Seq("foo", "bar", "fab", "tas")
    )
  )

}
