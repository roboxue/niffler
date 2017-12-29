package com.roboxue.niffler.monitoring

import java.io.IOException

import com.roboxue.niffler.{Logic, Niffler, Token}
import org.http4s.HttpService
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.{Server, staticcontent}

import scala.util.Try

/**
  * @author rxue
  * @since 12/27/17.
  */
object NifflerMonitor extends Niffler {
  final val nifflerMonitorService: Token[HttpService] = Token("niffler monitor service")
  final val nifflerMonitorServicePortNumber: Token[Int] = Token("port number for niffler monitor service")
  final val nifflerMonitorServiceRetry: Token[Int] = Token("number of attempts to find an open port")
  final val nifflerMonitorSubServices: Token[Seq[SubServiceWrapper]] = Token("a list of Sub Services")
  final val nifflerMonitorStartServer: Token[Server] = Token(
    "launch the monitor service and return the server instance"
  )

  $$(nifflerMonitorService.dependsOn(nifflerMonitorSubServices) { (subServices) =>
    import org.http4s.dsl._
    import org.http4s.twirl._
    HttpService {
      case GET -> Root =>
        Ok(html.nifflerMonitorIndex(subServices))
    }
  })

  $$(nifflerMonitorServicePortNumber.assign(4080))

  $$(nifflerMonitorServiceRetry.assign(5))

  $$(
    nifflerMonitorSubServices.amendWith(
      SubServiceWrapper(
        "Static File Service",
        "Serving static files",
        "/static",
        staticcontent.resourceService(staticcontent.ResourceService.Config(""))
      )
    )
  )

  $$(
    nifflerMonitorStartServer.dependsOn(
      nifflerMonitorService,
      nifflerMonitorServicePortNumber,
      nifflerMonitorServiceRetry,
      nifflerMonitorSubServices
    ) { (service, portNumber, retry, subServices) =>
      var attempt = 0
      var launchedServer: Option[Server] = None
      var builder = BlazeBuilder.mountService(service, "/")
      for (SubServiceWrapper(_, _, prefix, subService) <- subServices) {
        builder = builder.mountService(subService, prefix)
      }
      do {
        launchedServer = Try {
          val port = portNumber + attempt
          builder.bindHttp(port, "0.0.0.0").run
        }.toOption
        attempt += 1
      } while (launchedServer.isEmpty && attempt <= retry)
      launchedServer.getOrElse {
        throw new IOException(s"Cannot find an open port number between $portNumber and ${portNumber + attempt - 1}")
      }
    }
  )
}

case class SubServiceWrapper(serviceName: String,
                             serviceDetailsDescription: String,
                             servicePrefix: String,
                             service: HttpService)

object NifflerMonitorTest {
  def main(args: Array[String]): Unit = {
    Logic(Iterable(NifflerMonitor.nifflerMonitorServicePortNumber.assign({
      throw new Exception("hello world niffler")
    }))).asyncRun(NifflerMonitor.nifflerMonitorServicePortNumber)
    Niffler.combine(NifflerMonitor, ExecutionHistoryService).syncRun(NifflerMonitor.nifflerMonitorServicePortNumber)
    Niffler.combine(NifflerMonitor, ExecutionHistoryService).syncRun(NifflerMonitor.nifflerMonitorStartServer)
  }
}
