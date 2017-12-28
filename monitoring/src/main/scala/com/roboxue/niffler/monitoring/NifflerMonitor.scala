package com.roboxue.niffler.monitoring

import java.io.IOException

import com.roboxue.niffler.{Niffler, Token}
import org.http4s.HttpService
import org.http4s.server.blaze.BlazeBuilder

import scala.util.Try

/**
  * @author rxue
  * @since 12/27/17.
  */
trait NifflerMonitor {
  this: Niffler =>
  import NifflerMonitor.tokens._
  $$(nifflerMonitorService.dependsOn(nifflerMonitorSubServices) { (subServices) =>
    import org.http4s.dsl._
    HttpService {
      case GET -> Root =>
        Ok("Hello niffler")
    }
  })
  $$(nifflerMonitorServicePortNumber.assign(4080))
  $$(nifflerMonitorServiceRetry.assign(5))
  $$(nifflerMonitorSubServices.assign(Map.empty))
  $$(
    nifflerMonitorStartServer.dependsOn(
      nifflerMonitorService,
      nifflerMonitorServicePortNumber,
      nifflerMonitorServiceRetry,
      nifflerMonitorSubServices
    ) { (service, portNumber, retry, subServices) =>
      var attempt = 0
      var launchedPort: Option[Int] = None
      var builder = BlazeBuilder.mountService(service, "/")
      for ((prefix, subService) <- subServices) {
        builder = builder.mountService(subService, prefix)
      }
      do {
        launchedPort = Try {
          val port = portNumber + attempt
          builder.bindHttp(port, "0.0.0.0").run
          port
        }.toOption
        attempt += 1
      } while (launchedPort.isEmpty && attempt <= retry)
      launchedPort.getOrElse {
        throw new IOException(s"Cannot find an open port number between $portNumber and ${portNumber + attempt - 1}")
      }
    }
  )
}

object NifflerMonitor {
  object tokens {
    final val nifflerMonitorService: Token[HttpService] = Token("niffler monitor service")
    final val nifflerMonitorServicePortNumber: Token[Int] = Token("port number for niffler monitor service")
    final val nifflerMonitorServiceRetry: Token[Int] = Token("number of attempts to find an open port")
    final val nifflerMonitorSubServices: Token[Map[String, HttpService]] = Token(
      "a map of url prefix (start with /) and sub http services"
    )
    final val nifflerMonitorStartServer: Token[Int] = Token("launch the monitor service and return the port number")
  }
}
