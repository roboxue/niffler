package com.roboxue.niffler.monitoring.utils

import fs2.Task
import org.http4s.MediaType.`application/json`
import org.http4s.Response
import org.http4s.headers.`Content-Type`

/**
  * @author rxue
  * @since 12/28/17.
  */
trait ServiceUtils {
  import org.http4s.dsl._
  def jsonResponse(textResponse: Task[Response]): Task[Response] = {
    textResponse.withContentType(Some(`Content-Type`(`application/json`)))
  }

}
