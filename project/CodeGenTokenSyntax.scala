import java.io.{File, PrintWriter}

import sbt.{File, taskKey}

import scala.reflect.runtime.universe

/**
  * Automatically generate some code during "sbt compile"
  * @author rxue
  * @since 12/26/17.
  */
object CodeGenTokenSyntax {

  final val generateCode = taskKey[Seq[File]]("Generate boilerplate code for niffler core.")

  import universe._

  private def typeParameters(range: List[Int]): List[TypeDef] = {
    range.map(i => {
      TypeDef(Modifiers(), TypeName(s"T$i"), List.empty, TypeTree())
    })
  }

  private def tokenParameters(range: List[Int]): List[ValDef] = {
    range.map(i => {
      ValDef(Modifiers(), TermName(s"t$i"), q"Token[${TypeName(s"T$i")}]", EmptyTree)
    })
  }

  private def tokensWithoutType(range: List[Int]): List[Tree] = {
    range.map(i => {
      q"${Ident(TermName(s"t$i"))}"
    })
  }

  private def functionTypeParameter(range: List[Int]): List[Tree] = {
    range.map(i => {
      q"${TermName(s"T$i")}"
    })
  }

  private def getTokenFromCache(range: List[Int]): List[Tree] = {
    range.map(i => {
      q"cache(${Ident(TermName(s"t$i"))})"
    })
  }

  def requiresApplyCodeGen(length: Int): Tree = {
    val range = Range(1, length + 1).toList
    q"""def apply[..${typeParameters(range)}, T]
          (..${tokenParameters(range)})
          (f: (..${functionTypeParameter(range)}) => T): Formula[T] = {
          Formula[T](Set(..${tokensWithoutType(range)}), 
                             (cache) => f(..${getTokenFromCache(range)}))
        }
     """
  }

  def requiresCodeGen(count: Int): Tree = {
    val evalFunctions = Range(1, count + 1).toList.map(requiresApplyCodeGen)
    q"""
       object Requires {
         ..$evalFunctions
       }
     """
  }

  def generateCode(count: Int): Tree = {
    q"""
      package com.roboxue.niffler.syntax {
        import com.roboxue.niffler._

        ..${requiresCodeGen(count)}
      }
      """
  }

  def saveToFile(srcFolder: File): File = {
    srcFolder.mkdirs()
    val file = new File(srcFolder, "com/roboxue/niffler/syntax/TokenSyntax.scala")
    file.getParentFile.mkdirs()
    val writer = new PrintWriter(file)
    try {
      // Function types with 23 arity aren't supported by scala
      writer.println("// auto generated by sbt compile")
      writer.write(showCode(generateCode(22)))
      file
    } finally {
      writer.close()
    }
  }
}
