package com.roboxue.niffler

import org.scalatest.{FlatSpec, Matchers}

class CodeGenTest extends FlatSpec with Matchers {
  it should "generate scala code" in {
    val repl = CodeGen.generateScalaCode("com.roboxue.niffler.test", Seq(
      TokenRepresentation("contact", "43c91d79-5cfd-4e44-8dbe-3a53445fc540", "contact", "contact", "com.example.tutorial.AddressBookProtos.Person")
    ))
    repl shouldBe
      """package com.roboxue.niffler.test
        |
        |object NifflerTokens {
        |  val contact = Token[com.example.tutorial.Person]("contact", "43c91d79-5cfd-4e44-8dbe-3a53445fc540", "contact", "contact")
        |}
        |""".stripMargin
  }
}
