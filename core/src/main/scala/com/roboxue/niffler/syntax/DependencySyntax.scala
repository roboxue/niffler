package com.roboxue.niffler.syntax

import com.roboxue.niffler.execution.CacheFetcher
import com.roboxue.niffler.{DirectImplementation, ExecutionCache, Implementation, Token}
import shapeless._
import shapeless.ops.function.FnToProduct
import shapeless.ops.hlist.{Mapper, ToTraversable}

/**
  * provide syntax similar to =
  * @author rxue
  * @since 12/22/17.
  */
trait DependencySyntax[R] {
  thisToken: Token[R] =>

  /**
    *
    * @param value
    * @return
    */
  def assign(value: => R): Implementation[R] =
    Implementation(thisToken, new DirectImplementation[R](Set.empty) {
      override private[niffler] def forceEvaluate(cache: ExecutionCache): R = value
    })

  /**
    * Create an [[Implementation]] by declaring dependencies and providing a function to calculate result
    *
    * @param dependencies                          a variable length of tokens
    * @param implementation                        a function that take the same length of parameters as dependencies
    *                                              and returns [[R]]
    * @param dependenciesIsTokenList               auto convert dependencies to a list of Tokens
    * @param tokenListIsListOfTokens               code compiles only when every dependency is token
    * @param tokenListCanYieldVarTypeList          auto convert dependencies to VarList
    *                                              if dependencies is Tuple(Token[Int], Token[String], Token[Boolean],
    *                                              then VarList will be List(Int, String, Boolean)
    * @param implementationTakesVarListAndReturnsR code compiles only when implementation's conforms to VarList => R
    *                                              with example above, function should be (Int, String, Boolean) => R
    * @tparam TokenTuple   dependencies' type
    * @tparam TokenList    auto calculated dependencies' type in List format
    * @tparam FunctionType implementation function's type
    * @tparam ValueList    auto calculated function's input type based on [[TokenList]]
    * @return Auto generated [[Implementation]]
    */
  def dependsOn[TokenTuple, TokenList <: HList, ValueList <: HList, FunctionType](dependencies: TokenTuple)(
    implementation: FunctionType
  )(implicit dependenciesIsTokenList: Generic.Aux[TokenTuple, TokenList],
    tokenListIsListOfTokens: ToTraversable.Aux[TokenList, List, Token[_]],
    tokenListCanYieldVarTypeList: Mapper.Aux[CacheFetcher.type, TokenList, ValueList],
    implementationTakesVarListAndReturnsR: FnToProduct.Aux[FunctionType, ValueList => R]): Implementation[R] = {
    // convert token tuple to token hlist using shapeless evidences
    val dependencyHList: TokenList = dependenciesIsTokenList.to(dependencies)
    // convert token hlist to token set using shapeless evidences
    val tokenSet: Set[Token[_]] = tokenListIsListOfTokens(dependencyHList).toSet
    assert(!tokenSet.contains(thisToken), s"$thisToken cannot depend on itself using 'dependsOn'. use 'amend' instead")
    // create concrete implementation
    Implementation(thisToken, new DirectImplementation[R](tokenSet) {
      override private[niffler] def forceEvaluate(cache: ExecutionCache): R = {
        // Swap the dynamic variable "cacheBinding". This ensures we use the provided cache during CacheFetcher transform
        CacheFetcher.cacheBinding.withValue(cache) {
          // We have proven that TokenList can convert to ValueList using CacheFetcher
          // So now we convert a list of token to it's underlying type lists
          // e.g. List(Token[Int], Token[String], Token[Boolean]) to List(Int, String, Boolean)
          val valueList: ValueList = tokenListCanYieldVarTypeList(dependencyHList)
          // We have also proven that FunctionType is (ValueList) => R
          val f = implementationTakesVarListAndReturnsR(implementation)
          // Applying this function on the ValueList
          f(valueList)
        }
      }
    })
  }

  /**
    * Simplest version of creating an [[Implementation]] that depends on only one other token
    *
    * @param t1             other token that this token will depend on
    * @param implementation a function that takes [[T]] and returns [[R]]
    * @tparam T the type for the t1
    * @return an [[Implementation]]
    */
  def dependsOn[T](t1: Token[T])(implementation: (T) => R): Implementation[R] = {
    assert(t1 != thisToken, s"$thisToken cannot depend on itself using 'dependsOn'. use 'amend' instead")
    Implementation(thisToken, new DirectImplementation[R](Set(t1)) {
      override private[niffler] def forceEvaluate(cache: ExecutionCache): R = {
        implementation(cache(t1))
      }
    })
  }

}
