package com.roboxue.niffler.syntax

import com.roboxue.niffler._
import com.roboxue.niffler.execution.CacheFetcher
import shapeless._
import shapeless.ops.function.FnToProduct
import shapeless.ops.hlist.{Mapper, ToTraversable}

/**
  * provide syntax similar to =
  *
  * @author rxue
  * @since 12/22/17.
  */
trait DependencySyntax[R] {
  thisToken: Token[R] =>

  /**
    * Special situation when this token is implemented without any dependencies
    *
    * @param value           a lazy function knows how to calculate the value
    * @param addToCollection if provided (provided automatically inside a [[Niffler]] trait), add the return value to it
    * @return an [[Implementation]]
    */
  def assign(value: => R)(implicit addToCollection: Niffler = null): Implementation[R] = {
    val impl = new Implementing[() => R, R] {
      override protected type _TokenList = HNil
      override protected type _ValueList = HNil
      override protected val _tokenListIsListOfTokens: ToTraversable.Aux[_TokenList, List, Token[_]] =
        implicitly[ToTraversable.Aux[_TokenList, List, Token[_]]]
      override protected val _tokenListCanYieldVarTypeList: Mapper.Aux[CacheFetcher.type, _TokenList, _ValueList] =
        implicitly
      override protected val _implementationTakesVarListAndReturnsR: FnToProduct.Aux[() => R, HNil => R] =
        implicitly
      override protected val _tokenAmended: Token[R] = thisToken
      override protected val _dependingTokenHList: _TokenList = HNil
    }.usingFunction(() => value)
    Option(addToCollection).foreach(c => c.addImpl(impl))
    impl
  }

  /**
    * Create an [[Implementing]] that will take a list of [[Token]] and calculate the function type needed
    * by declaring dependencies and providing a function to calculate result
    *
    * DON'T PANIC, all type parameters are figured out automatically using shapeless, as long as the "dependencies" is
    * a list of Token. That's why there is no doc strings for them
    *
    * @param dependencies a variable length of tokens
    * @return Auto generated [[Implementing]]
    *
    *         {code}
    *         val t1: Token[String] = Token("a string")
    *         val t2: Token[Int] = Token("an int")
    *         val t3: Token[Int] = Token("another int")
    *         val t3Impl: Implementation[Int] = t3.dependsOn(t1, t2) {
    *         (v1: String, v2: Int) =>
    *         v1.length + v2
    *         }
    *         val logic1: Logic = Logic(Seq(t1.assign("hello"), t2.assign(3), t3Impl))
    *         logic1.syncRun(t3).result shouldBe 8 // hello.length == 5, 5 + 3 == 8
    *         val logic2: Logic = Logic(Seq(t3Impl))
    *         logic2.syncRun(t3, ExecutionCache.fromValue(Map(t1 -> "wow", t2 -> 6))).result shouldBe 9 // wow.length == 3, 3 + 6 == 9
    *         {code}
    *         when the resultImpl is added to a [[com.roboxue.niffler.Logic]] and evaluated with a [[ExecutionCache]]
    *         The code example above will use the runtime value of t1 and t2 (assign to v1 and v2 correspondingly),
    *         then execute the function body
    *
    */
  def dependsOn[TokenTuple <: Product, TokenList <: HList, ValueList <: HList, FunctionType](dependencies: TokenTuple)(
    //---- the implicit values below is an automated process to calculate FunctionType based on TokenTuple
    implicit dependenciesIsTokenList: Generic.Aux[TokenTuple, TokenList],
    tokenListIsListOfTokens: ToTraversable.Aux[TokenList, List, Token[_]],
    tokenListCanYieldVarTypeList: Mapper.Aux[CacheFetcher.type, TokenList, ValueList],
    implementationTakesVarListAndReturnsR: FnToProduct.Aux[FunctionType, ValueList => R]
    //---- if TokenTuple is (Token[A]), FunctionType will be calculated to (A) => R
    //---- if TokenTuple is (Token[A], Token[B], Token[C]), FunctionType will be calculated to (A, B, C) => R
  ): Implementing[FunctionType, R] = {
    // convert token tuple to token hlist using shapeless evidences
    val dependingTokenHList: TokenList = dependenciesIsTokenList.to(dependencies)
    assert(
      !tokenListIsListOfTokens(dependingTokenHList).contains(thisToken),
      s"$thisToken cannot depend on itself using 'dependsOn'. use 'amend' instead"
    )
    new Implementing[FunctionType, R] {
      override protected type _TokenList = TokenList
      override protected type _ValueList = ValueList
      override protected val _tokenListIsListOfTokens: ToTraversable.Aux[_TokenList, List, Token[_]] =
        tokenListIsListOfTokens
      override protected val _tokenListCanYieldVarTypeList: Mapper.Aux[CacheFetcher.type, _TokenList, _ValueList] =
        tokenListCanYieldVarTypeList
      override protected val _implementationTakesVarListAndReturnsR: FnToProduct.Aux[FunctionType, _ValueList => R] =
        implementationTakesVarListAndReturnsR
      override protected val _tokenAmended: Token[R] = thisToken
      override protected val _dependingTokenHList: _TokenList = dependingTokenHList

    }
  }

  /**
    * Simplest version of creating an [[Implementing]] that depends on only one other token
    *
    * @param t1 other token that this token will depend on
    * @tparam T the type for the t1
    * @return an [[Implementing]]
    */
  def dependsOn[T](t1: Token[T]): Implementing[(T) => R, R] = {
    assert(t1 != thisToken, s"$thisToken cannot depend on itself using 'dependsOn'. use 'amend' instead")
    new Implementing[(T) => R, R] {
      override protected type _TokenList = Token[T] :: HNil
      override protected type _ValueList = T :: HNil
      override protected val _tokenListIsListOfTokens: ToTraversable.Aux[_TokenList, List, Token[_]] =
        implicitly[ToTraversable.Aux[_TokenList, List, Token[_]]]
      override protected val _tokenListCanYieldVarTypeList: Mapper.Aux[CacheFetcher.type, _TokenList, _ValueList] =
        implicitly[Mapper.Aux[CacheFetcher.type, _TokenList, _ValueList]]
      override protected val _implementationTakesVarListAndReturnsR: FnToProduct.Aux[(T) => R, _ValueList => R] =
        implicitly
      override protected val _tokenAmended: Token[R] = thisToken
      override protected val _dependingTokenHList: _TokenList = t1 :: HNil
    }
  }

}

sealed trait Implementing[FunctionType, R] {
  protected type _TokenList <: HList
  protected type _ValueList <: HList
  protected val _tokenListIsListOfTokens: ToTraversable.Aux[_TokenList, List, Token[_]]
  protected val _tokenListCanYieldVarTypeList: Mapper.Aux[CacheFetcher.type, _TokenList, _ValueList]
  protected val _implementationTakesVarListAndReturnsR: FnToProduct.Aux[FunctionType, _ValueList => R]
  protected val _tokenAmended: Token[R]
  protected val _dependingTokenHList: _TokenList

  /**
    * Complete the amending process with an implementation function
    *
    * @param function        a function that take [[R]] and the same length of parameters
    *                        as dependencies and returns [[R]]
    * @param addToCollection if provided (provided automatically inside a [[Niffler]] trait), add the return value to it
    * @return
    */
  def usingFunction(function: FunctionType)(implicit addToCollection: Niffler = null): Implementation[R] = {
    // convert token hlist to token set using shapeless evidences
    val tokenSet: Set[Token[_]] = _tokenListIsListOfTokens(_dependingTokenHList).toSet
    // create concrete implementation
    val impl = Implementation(_tokenAmended, new DirectImplementation[R](tokenSet) {
      override private[niffler] def forceEvaluate(cache: ExecutionCache): R = {
        // Swap the dynamic variable "cacheBinding". This ensures we use the provided cache during CacheFetcher transform
        CacheFetcher.cacheBinding.withValue(cache) {
          // We have proven that TokenList can convert to ValueList using CacheFetcher
          // So now we convert a list of token to it's underlying type lists
          // e.g. List(Token[Int], Token[String], Token[Boolean]) to List(Int, String, Boolean)
          val valueList: _ValueList = _tokenListCanYieldVarTypeList(_dependingTokenHList)
          // Applying this function on the ValueList plus existing value
          // Now the result is an amendment to previous execution's result
          val f = _implementationTakesVarListAndReturnsR(function)
          f(valueList)
        }
      }
    })
    Option(addToCollection).foreach(c => c.addImpl(impl))
    impl
  }

}
