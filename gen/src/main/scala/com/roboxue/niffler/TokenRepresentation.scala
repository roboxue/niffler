package com.roboxue.niffler

case class TokenRepresentation(codeName: String,
                               uuid: String,
                               summaryName: String,
                               description: String,
                               typeName: String) {

  def toScala: String = {
    s"""val $codeName = Token[$typeName]("$codeName", "$uuid", "$summaryName", "$description")"""
  }

  def toPython: String = {
    s"""$codeName = Token("$codeName", "$uuid", "$summaryName", "$description", "$typeName")"""
  }
}
