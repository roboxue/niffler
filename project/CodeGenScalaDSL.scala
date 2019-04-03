import java.io.PrintWriter

import sbt.{File, taskKey}

import scala.collection.immutable

/**
  * Automatically generate some code during "sbt compile"
  *
  * @author robert xue
  * @since 12/26/17.
  */
object CodeGenScalaDSL {
  val FORMULA = "Formula"
  val DATA_FLOW = "DataFlow"
  val TOKEN = "Token"

  def genericParam(prefix: String, count: Int): immutable.Seq[String] = (1 to  count).map(c => s"$prefix$c")

  def params(namePrefix: String, typePrefix: String, wrapper: Option[String], count: Int): immutable.Seq[String] = {
    (1 to  count).map(c => {
      wrapper match {
        case Some(w) =>
          s"$namePrefix$c: $w[$typePrefix$c]"
        case None =>
          s"$namePrefix$c: $typePrefix$c"
      }
    })
  }

  def generateFormulaCode(depCount: Int): String = {
    require(depCount > 0)
    val p = params("t", "T", Some(TOKEN), depCount).mkString(",")
    val T = genericParam("T", depCount).mkString(",")
    val tokens = genericParam("t", depCount).mkString(",")
    val stateApply = (1 to  depCount).map(c => s"state(t$c)").mkString(",")

    s"""case class $FORMULA$depCount[$T, R]($p, outlet: $TOKEN[R]) extends $FORMULA[R] {
       |  override val dependsOn: Seq[$TOKEN[_]] = Seq($tokens)
       |
       |  def :=(impl: ($T) => R): $DATA_FLOW[R] = implBy(impl)
       |
       |  def :=>(futureImpl: ($T) => Future[R]): $DATA_FLOW[R] = implByFuture(futureImpl)
       |
       |  def implBy(impl: ($T) => R): $DATA_FLOW[R] =
       |    new Sync$DATA_FLOW[R](dependsOn, outlet, state => impl($stateApply))
       |
       |  def implByFuture(futureImpl: ($T) => Future[R]): Async$DATA_FLOW[R] =
       |    new Async$DATA_FLOW[R](dependsOn, outlet, state => futureImpl($stateApply))
       |}
     """.stripMargin
  }

  final val generateScalaCode = taskKey[Seq[File]]("Generate boilerplate scala code for niffler core.")

  def saveToFile(srcFolder: File): File = {
    srcFolder.mkdirs()
    val file = new File(srcFolder, "com/roboxue/niffler/scalaDSL/AutoGeneratedSyntax.scala")
    file.getParentFile.mkdirs()
    val writer = new PrintWriter(file)
    try {
      // Function types with 23 arity aren't supported by scala
      writer.println("// auto generated by sbt compile")
      writer.println("package com.roboxue.niffler.scalaDSL")
      writer.println("import scala.concurrent.Future")
      writer.println("import com.roboxue.niffler.{AsyncDataFlow, DataFlow, SyncDataFlow, Token}")
      writer.println()
      val maxArity = 20
      (1 to maxArity).foreach(i => {
        writer.println(generateFormulaCode(i))
      })
      file
    } finally {
      writer.close()
    }
  }
}