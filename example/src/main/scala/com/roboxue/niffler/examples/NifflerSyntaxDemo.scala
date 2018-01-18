package com.roboxue.niffler.examples

import com.roboxue.niffler._
import com.roboxue.niffler.execution.NifflerInvocationException
import com.roboxue.niffler.monitoring.{ExecutionHistoryService, NifflerMonitor}
import com.roboxue.niffler.syntax.{Constant, Requires}

import scala.util.Try

/**
  * @author rxue
  * @since 1/1/18.
  */
object NifflerSyntaxDemo {
  //noinspection SimplifyBoolean
  def main(args: Array[String]): Unit =
    try {
      // Start the niffler monitor UI so we can check the execution status right away
      Niffler.combine(NifflerMonitor, ExecutionHistoryService).syncRun(NifflerMonitor.nifflerMonitorStartServer)
      // let's open http://localhost:4080/history to view all the operation we have done!

      // the import above provides extra syntax sugar that is automatically available when writing code inside a "Niffler" trait
      // Token[T] is the base unit of "data".
      val email: Token[String] = Token("user email")
      val password: Token[String] = Token("user password")
      val login: Token[Boolean] = Token("attempt to login")
      // Token doesn't 'hold' the data, instead it holds meta data like the doc string of this data (`codeName` and `name`)
      // and the type of the data (`returnTypeDescription`)
      assert(email.name == "user email")
      assert(email.codeName == "email")
      assert(email.returnTypeDescription == "String")

      // DataFlowOperation[T] is the base unit of "algorithm/function". It guaranteed to yield `T` upon evaluation
      // It takes two parameters to construct
      // the first param is the list of `Token` it depends on
      // the second param is a lambda function to use the return values of dependencies and yield a `T`
      val logicPart1: DataFlowOperation[Boolean] =
        login := Requires(email, password)({ (email: String, password: String) =>
          true
        })
      // the type signature in the lambda function in the example above can be ignored
      val logicPart2: DataFlowOperation[Boolean] = login := Requires(email, password)({ (email, password) =>
        true
      })
      // the param name in the lambda function can be anything you like
      val logicPart3: DataFlowOperation[Boolean] = login := Requires(email, password)({ (userEmail, userPassword) =>
        userEmail == userPassword
      })

      // a pure function can be provided instead of a lambda function
      def alwaysSuccessfulLogin(email: String, password: String): Boolean = {
        true
      }

      val logicPart4: DataFlowOperation[Boolean] = login := Requires(email, password)(alwaysSuccessfulLogin)

      // another way to create a DataFlowOperation is by creating formula
      val formula: Formula[Boolean] = Requires(email, password)(alwaysSuccessfulLogin)
      val logicPart5: DataFlowOperation[Boolean] = login := formula

      // if you depends on only one token, you can use Token.mapFormula(f) instead of Requires(token)(f)
      val logicPart6: DataFlowOperation[Boolean] = login := email.mapFormula(_.nonEmpty)

      // Niffler is a collection of LogicParts.
      // Niffler is a trait, usually you'll create an Object to extend Niffler to provide static reference to tokens
      // Niffler provides grammar sugars to easily add LogicParts
      object NifflerDemo extends Niffler {
        // use protected method addLogicPart to add DataFlowOperation to niffler. This ensures niffler is immutable upon creation
        addLogicPart(logicPart4)
        // $$ works as well if you don't want to type 'addLogicPart'
        // DataFlowOperation for the same token will override existing DataFlowOperation,
        // thus logicPart4 has been overridden by the following DataFlowOperation, since they all implements `login`
        $$(login := Requires(email, password)({ (email, password) =>
          email == password
        }))
      }

      // Logic is an immutable collection of DataFlowOperation similar to Niffler. In fact any niffler can be output to a logic
      val logic1: Logic = NifflerDemo.getLogic
      // you can certainly query logic for DataFlowOperation, although this is no a popular use case
      assert(logic1.implForToken(login) != null)
      // this is how you invoke the evaluation of a token from a logic / niffler
      val r1 = Try(logic1.syncRun(login))
      // niffler is a logic, so the same syntax applies
      val r2 = Try(NifflerDemo.syncRun(login))
      // r1 and r2 is going to fail because there is no impl for email and password, so eval of login cannot proceed
      // this is almost identical to NullPointerExceptions when calling a normal function and provide null for parameters
      assert(r1.failed.get.isInstanceOf[NifflerInvocationException])
      assert(r2.failed.get.isInstanceOf[NifflerInvocationException])
      // we can provide the missing impl when invoking the logic
      val r3 = Try(
        NifflerDemo.syncRun(login, Seq(email := Constant("roboxue@roboxue.com"), password := Constant("password")))
      )
      // now we shall fail the login
      assert(r3.isSuccess)
      val ExecutionResult(result3, _, _) = r3.get
      assert(result3 == false)
      // because in NifflerDemo, the login impl is `email == password`, the following shall yields true
      val r4 = NifflerDemo.syncRun(
        login,
        Seq(email := Constant("roboxue@roboxue.com"), password := Constant("roboxue@roboxue.com"))
      )
      val ExecutionResult(result4, _, _) = r4
      assert(result4 == true)
      // we can also inject a new DataFlowOperation to login to force a successful login
      // this is equivalent to a runtime override
      assert(
        NifflerDemo
          .syncRun(login, Seq(email := Constant("roboxue@roboxue.com"), password := Constant("password"), logicPart4))
          .result == true
      )
      // more powerful than an override, this can change the dependency list as well.
      // Previously you need both email and password to login, now you can ignore password field
      assert(
        NifflerDemo
          .syncRun(login, Seq(email := Constant("foo@roboxue.com"), login := email.mapFormula { email =>
            email.contains("roboxue.com")
          }))
          .result == true
      )
      // logic.diverge do the same thing
      val logic2: Logic = NifflerDemo.diverge(Seq(login := email.mapFormula { email =>
        email.contains("roboxue.com")
      }))
      assert(Try(NifflerDemo.syncRun(login, Seq(email := Constant("bar@roboxue.com")))).isSuccess == false)
      assert(logic2.syncRun(login, Seq(email := Constant("bar@roboxue.com"))).result == true)

      // ExecutionCache is where the data being stored
      val ExecutionResult(_, _, cacheOfR3) = r3.get
      cacheOfR3.isInstanceOf[ExecutionCache]
      // You can query the cache for sure, and it is type safe
      assert(cacheOfR3(email) == "roboxue@roboxue.com")
      assert(cacheOfR3(password) == "password")
      // cache can be reused,
      // although login depends on email & password which has no impl, they do exist in the cache.
      // So the eval is still successful
      assert(NifflerDemo.syncRun(login, cache = cacheOfR3).result == false)
      // and here is a complete example where you invoke the eval of a token use a prepackaged NifflerDemo
      // with extra override and a non-empty cache
      assert(
        NifflerDemo
          .syncRun(login, extraImpl = Seq(login := password.mapFormula { _ == "password" }), cache = cacheOfR3)
          .result == true
      )
    } finally {
      // always call Niffler.terminate to stop the akka system that is backing it.
      // unless you want this to be a daemon instance (i.e. running a server)
      Niffler.terminate()
    }
}
