package com.roboxue.niffler

import org.scalatest.{FlatSpec, Matchers}

class NifflerExportingTest extends FlatSpec with Matchers {
  it should "scan" in {
    val result = NifflerExporting.scan()
    result.length shouldBe 3 // see annotated methods in object IntegrationPointTest
    NifflerExporting.main(Array.empty)
  }
}
