package com.roboxue.niffler

import com.google.common.reflect.ClassPath
import org.apache.commons.lang3.reflect.MethodUtils

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import org.json4s.jackson.JsonMethods._
import org.json4s.JsonDSL._

object NifflerExporting {
  def main(args: Array[String]): Unit = {
    for (integrationPoint <- scan()) {
      val impl = integrationPoint.instantiate
      val jObj = ("name" -> impl.name) ~
        ("classname" -> impl.className) ~
        ("line" -> impl.line) ~
        ("dependencies" -> impl.dependencies.map(_.uuid))
      // TODO: add cli args to write to a file
      println(compact(jObj))
    }
  }

  def scan(): Seq[ClassAndMethodName] = {
    val results = ListBuffer[ClassAndMethodName]()
    val clazzOfIntegrationPoint = classOf[IntegrationPoint]
    for (c <- ClassPath.from(getClass.getClassLoader).getTopLevelClasses.asScala) {
      try {
        val clazz = c.load()
        for (m <- MethodUtils.getMethodsWithAnnotation(clazz, clazzOfIntegrationPoint)) {
          if (classOf[Implementation[_]].isAssignableFrom(m.getReturnType)) {
            results += ClassAndMethodName(clazz.getName, m.getName)
          }
        }
      } catch {
        case _: Throwable =>
      }
    }
    results
  }
}

case class ClassAndMethodName(className: String, methodName: String) {
  def instantiate: Implementation[_] = {
    Class.forName(className)
      .getMethod(methodName).invoke(null).asInstanceOf[Implementation[_]]
  }
}
