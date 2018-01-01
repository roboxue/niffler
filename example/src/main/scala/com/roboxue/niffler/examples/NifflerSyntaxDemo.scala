package com.roboxue.niffler.examples

import com.roboxue.niffler._
import com.roboxue.niffler.execution.NifflerInvocationException
import org.jgrapht.graph.{DefaultEdge, DirectedAcyclicGraph}

import scala.util.Try

/**
  * @author rxue
  * @since 1/1/18.
  */
object NifflerSyntaxDemo {
  //noinspection SimplifyBoolean
  def main(args: Array[String]): Unit =
    try {

      // Token[T] is the base unit of "data".
      val email: Token[String] = Token("user email")
      val password: Token[String] = Token("user password")
      val login: Token[Boolean] = Token("attempt to login")
      // Token doesn't 'hold' the data, instead it holds meta data like the doc string of this data (`codeName` and `name`)
      // and the type of the data (`returnTypeDescription`)
      assert(email.name == "user email")
      assert(email.codeName == "email")
      assert(email.returnTypeDescription == "String")

      // Implementation[T] is the base unit of "algorithm/function". It guaranteed to yield `T` upon evaluation
      // It takes two parameters to construct
      // the first param is the list of `Token` it depends on
      // the second param is a lambda function to use the return values of dependencies and yield a `T`
      val loginImplementation1: Implementation[Boolean] =
        login.dependsOn(email, password)({ (email: String, password: String) =>
          true
        })
      // the type signature in the lambda function in the example above can be ignored
      val loginImplementation2: Implementation[Boolean] = login.dependsOn(email, password)({ (email, password) =>
        true
      })
      // the param name in the lambda function can be anything you like
      val loginImplementation3: Implementation[Boolean] = login.dependsOn(email, password)({
        (userEmail, userPassword) =>
          userEmail == userPassword
      })
      // a pure function can be provided instead of a lambda function
      def alwaysSuccessfulLogin(email: String, password: String): Boolean = {
        true
      }
      val loginImplementation4: Implementation[Boolean] = login.dependsOn(email, password)(alwaysSuccessfulLogin)

      // Niffler is a collection of implementations.
      import com.roboxue.niffler.Niffler
      // Niffler is a trait, usually you'll create an Object to extend Niffler to provide static reference to tokens
      // Niffler provides grammar sugars to easily add implementations
      object NifflerDemo extends Niffler {
        // create token in this object so that they can be statically referenced
        val nEmail: Token[String] = Token("user email")
        val nPassword: Token[String] = Token("user password")
        val nLogin: Token[Boolean] = Token("attempt to login")
        // use protected method addImpl to add implementation to niffler. This ensures niffler is immutable upon creation
        addImpl(nLogin.dependsOn(nEmail, nPassword)({ (email, password) =>
          email == password
        }))
        // $$ works as well if you don't want to type 'addImpl'
        // implementation for the same token will override existing implementation
        $$(nLogin.dependsOn(nEmail, nPassword)({ (email, password) =>
          email == password
        }))
      }

      // Logic is an immutable collection of implementation similar to Niffler. In fact any niffler can be output to a logic
      val logic1: Logic = NifflerDemo.getLogic
      // you can certainly query logic for implementation, although this is no a popular use case
      assert(logic1.implForToken(NifflerDemo.nLogin) != null)
      // logic one to one maps to a DAG
      assert(logic1.topology.isInstanceOf[DirectedAcyclicGraph[Token[_], DefaultEdge]])
      // this is how you invoke the evaluation of a token from a logic / niffler
      val r1 = Try(logic1.syncRun(NifflerDemo.nLogin))
      // niffler is a logic, so the same syntax applies
      val r2 = Try(NifflerDemo.syncRun(NifflerDemo.nLogin))
      // r1 and r2 is going to fail because there is no impl for nEmail and nPassword, so eval of nLogin cannot proceed
      // this is almost identical to NullPointerExceptions when calling a normal function and provide null for parameters
      assert(r1.failed.get.isInstanceOf[NifflerInvocationException])
      // we can provide the missing impl when invoking the logic
      val r3 = Try(
        NifflerDemo.syncRun(
          NifflerDemo.nLogin,
          Seq(NifflerDemo.nEmail.assign("roboxue@roboxue.com"), NifflerDemo.nPassword.assign("password"))
        )
      )
      // now we shall fail the login
      assert(r3.isSuccess)
      val ExecutionResult(result, _, _) = r3.get
      assert(result == false)
      // we can even inject a new implementation to nLogin to force a successful login
      // this is equivalent to a runtime override
      assert(
        NifflerDemo
          .syncRun(
            NifflerDemo.nLogin,
            Seq(
              NifflerDemo.nEmail.assign("roboxue@roboxue.com"),
              NifflerDemo.nPassword.assign("password"),
              NifflerDemo.nLogin.dependsOn(NifflerDemo.nEmail, NifflerDemo.nPassword)(alwaysSuccessfulLogin)
            )
          )
          .result == true
      )
      // more powerful than an override, this can change the dependency list as well
      assert(NifflerDemo.syncRun(NifflerDemo.nLogin, Seq(NifflerDemo.nLogin.assign(true))).result == true)
      // logic.diverge do the same thing
      val logic2: Logic = NifflerDemo.diverge(Seq(NifflerDemo.nLogin.assign(true)))
      assert(logic2.syncRun(NifflerDemo.nLogin).result == true)

      // ExecutionCache is where the data being stored
      val ExecutionResult(_, _, cacheOfR3) = r3.get
      cacheOfR3.isInstanceOf[ExecutionCache]
      // You can query the cache for sure, and it is type safe
      assert(cacheOfR3(NifflerDemo.nEmail) == "roboxue@roboxue.com")
      assert(cacheOfR3(NifflerDemo.nPassword) == "password")
      // cache can be reused,
      // although nLogin depends on nEmail & nPassword which has no impl, they do exist in the cache.
      // So the eval is still successful
      assert(NifflerDemo.syncRun(NifflerDemo.nLogin, cache = cacheOfR3).result == false)
      // and here is a complete example where you invoke the eval of a token use a prepackaged NifflerDemo
      // with extra override and a non-empty cache
      assert(
        NifflerDemo
          .syncRun(NifflerDemo.nLogin, extraImpl = Seq(NifflerDemo.nLogin.dependsOn(NifflerDemo.nPassword) {
            _ == "password"
          }), cache = cacheOfR3)
          .result == false
      )

    } finally {
      // always call Niffler.terminate to stop the akka system that is backing it.
      Niffler.terminate()
    }
}
