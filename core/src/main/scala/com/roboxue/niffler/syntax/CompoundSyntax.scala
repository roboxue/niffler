package com.roboxue.niffler.syntax

import com.roboxue.niffler.execution.CacheFetcher
import com.roboxue.niffler.{ExecutionCache, Implementation, IncrementalImplementation, Token}
import shapeless._
import shapeless.ops.function.FnToProduct
import shapeless.ops.hlist.{Mapper, ToTraversable}

/**
  * provide syntax similar to +=
  * @author rxue
  * @since 12/22/17.
  */
trait CompoundSyntax[R] {
  thisToken: Token[R] =>

  /**
    * Create an [[Implementation]] that will amend this token's existing value in cache during runtime
    * by declaring dependencies and providing a function to calculate result
    *
    * @param dependencies                          a variable length of tokens
    * @param implementation                        a function that take [[R]] and the same length of parameters
    *                                              as dependencies and returns [[R]]
    * @param dependenciesIsTokenList               auto convert dependencies to a list of Tokens
    * @param tokenListIsListOfTokens               code compiles only when every dependency is token
    * @param tokenListCanYieldVarTypeList          auto convert dependencies to VarList
    *                                              if dependencies is Tuple(Token[Int], Token[String], Token[Boolean],
    *                                              then VarList will be List(Int, String, Boolean)
    * @param implementationTakesVarListAndReturnsR code compiles only when implementation conforms to R :: VarList => R
    *                                              with example above, function should be (R, Int, String, Boolean) => R
    * @tparam TokenTuple   dependencies' type
    * @tparam TokenList    auto calculated dependencies' type in List format
    * @tparam FunctionType implementation function's type
    * @tparam ValueList    auto calculated function's input type based on [[TokenList]]
    * @return Auto generated [[Implementation]]
    *
    * {code}
    * val t1: Token[String] = Token("a string")
    * val t2: Token[Int] = Token("an int")
    * val t3: Token[Int] = Token("another int")
    * val t3Impl: Implementation[Int] = t3.dependsOn(t1) {
    *   (v1: String) =>
    *     v1.length
    * }
    * val t3Amend: Implementation[Int] = t3.amendWith(t2) {
    *   (v3: Int, v2: Int) =>
    *     v3 + v2
    * }
    * val logic1: Logic = Logic(Seq(t1.assign("hello"), t2.assign(3), t3Impl, t3Amend))
    * logic1.syncRun(t3).result shouldBe 8 // hello.length == 5, 5 + 3 == 8
    * val logic2: Logic = Logic(Seq(t3Impl, t3.amend))
    * logic2.syncRun(t3, ExecutionCache.fromValue(Map(t1 -> "wow", t2 -> 6))).result shouldBe 9 // wow.length == 3, 3 + 6 == 9
    * val logic3: Logic = Logic(Seq(t3Amend))
    * logic3.syncRun(t3, ExecutionCache.fromValue(Map(t1 -> "wow", t2 -> 6, t3 -> 42))).result shouldBe 48 // 42 + 6 == 48
    * {code}
    * when the resultImpl is added to a [[com.roboxue.niffler.Logic]] and evaluated with a [[ExecutionCache]]
    * The code example above will use the runtime value of t1 and t2 (assign to v1 and v2 correspondingly),
    * then execute the function body
    *
    */
  def amendWith[TokenTuple, TokenList <: HList, ValueList <: HList, FunctionType](dependencies: TokenTuple)(
    implementation: FunctionType
  )(implicit dependenciesIsTokenList: Generic.Aux[TokenTuple, TokenList],
    tokenListIsListOfTokens: ToTraversable.Aux[TokenList, List, Token[_]],
    tokenListCanYieldVarTypeList: Mapper.Aux[CacheFetcher.type, TokenList, ValueList],
    implementationTakesVarListAndReturnsR: FnToProduct.Aux[FunctionType, R :: ValueList => R]): Implementation[R] = {
    // convert token tuple to token hlist using shapeless evidences
    val dependencyHList: TokenList = dependenciesIsTokenList.to(dependencies)
    // convert token hlist to token set using shapeless evidences
    val tokenSet: Set[Token[_]] = tokenListIsListOfTokens(dependencyHList).toSet
    assert(
      !tokenSet.contains(thisToken),
      s"There is no need for $thisToken to declare dependencies on itself when using 'amend'"
    )
    // create concrete implementation
    Implementation(thisToken, new IncrementalImplementation[R](tokenSet) {
      override private[niffler] def forceEvaluate(cache: ExecutionCache, existingValue: R): R = {
        // Swap the dynamic variable "cacheBinding". This ensures we use the provided cache during CacheFetcher transform
        CacheFetcher.cacheBinding.withValue(cache) {
          // We have proven that TokenList can convert to ValueList using CacheFetcher
          // So now we convert a list of token to it's underlying type lists
          // e.g. List(Token[Int], Token[String], Token[Boolean]) to List(Int, String, Boolean)
          val valueList: ValueList = tokenListCanYieldVarTypeList(dependencyHList)
          // We have also proven that FunctionType is (ValueList) => R
          val f = implementationTakesVarListAndReturnsR(implementation)
          // Applying this function on the ValueList plus existing value
          // Now the result is an amendment to previous exection's result
          f(existingValue :: valueList)
        }
      }
    })
  }

  /**
    * Simplest version of creating an [[Implementation]] that amend this token's existing value in cache during runtime
    * and depends on only one other token
    *
    * @param t1             other token that this token will depend on
    * @param implementation a function that takes [[R]] and [[T]] and returns [[R]]
    * @tparam T the type for the t1
    * @return an [[Implementation]]
    */
  def amendWith[T](t1: Token[T])(implementation: (R, T) => R): Implementation[R] = {
    assert(t1 != thisToken, s"$thisToken cannot depend on itself using 'dependsOn'. use 'amend' instead")
    Implementation(thisToken, new IncrementalImplementation[R](Set(t1)) {
      override private[niffler] def forceEvaluate(cache: ExecutionCache, existingValue: R): R = {
        implementation(existingValue, cache(t1))
      }
    })
  }

  /**
    * Simplest version of creating an [[Implementation]] that amend this token's existing value in cache during runtime
    * and depends on only one other token
    *
    * @param implementation a function that takes [[R]] and returns [[R]]
    * @return an [[Implementation]]
    */
  def amend(implementation: R => R): Implementation[R] = {
    Implementation(thisToken, new IncrementalImplementation[R](Set.empty) {
      override private[niffler] def forceEvaluate(cache: ExecutionCache, existingValue: R): R = {
        implementation(existingValue)
      }
    })
  }
}
