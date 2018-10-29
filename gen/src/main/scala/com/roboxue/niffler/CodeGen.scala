package com.roboxue.niffler

object CodeGen {

  def generateScalaCode(packageName: String, tokens: Seq[TokenRepresentation], indent: Int = 2): String = {
    val tokensRepl = tokens.map(" " * indent + _.toScala).mkString(System.lineSeparator())
    s"""package $packageName
       |
       |object NifflerTokens {
       |$tokensRepl
       |}
       |""".stripMargin
  }

  def generatePythonCode(packageName: String, tokens: Seq[TokenRepresentation], indent: Int = 4): String = {
    val tokensRepl = tokens.map(" " * indent + _.toPython).mkString(System.lineSeparator())
    s"""class NifflerTokens(object):
       |$tokensRepl
     """.stripMargin
  }

}
