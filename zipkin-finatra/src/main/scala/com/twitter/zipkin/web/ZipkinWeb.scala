package com.twitter.zipkin.web

import com.twitter.zipkin.config.ZipkinWebConfig
import com.posterous.finatra.FinatraServer.FinatraService
import com.twitter.finagle.builder.{ServerBuilder, Server}
import com.twitter.finagle.http.Http
import java.net.InetSocketAddress
import com.twitter.ostrich.admin.{ServiceTracker, Service}
import com.twitter.logging.Logger
import com.posterous.finatra.{FinatraResponse, FinatraApp, FileHandler, FinatraServer}
import com.twitter.io.{Files, TempFile}

class ZipkinWeb(config: ZipkinWebConfig) extends Service {

  val log = Logger.get()
  var server: Option[Server] = None

  def start() {
    val resource = config.resource
    val app = config.app

    FinatraServer.register(resource)
    FinatraServer.register(app)
    FinatraServer.layoutHelperFactory = new ZipkinLayoutHelperFactory

    Globals.rootUrl = config.rootUrl

    val finatraService = new FinatraService
    val fileHandler = new FileHandler
    val service = fileHandler andThen finatraService

    server = Some {
      ServerBuilder()
        .codec(Http())
        .bindTo(new InetSocketAddress(config.serverPort))
        .name("ZipkinWeb")
        .build(service)
    }
    log.info("Finatra service started in port: " + config.serverPort)
    ServiceTracker.register(this)
  }

  def shutdown() {
    server.foreach { _.close() }
  }
}

class Resource(resourceDirs: Map[String, String]) extends FinatraApp {
  resourceDirs.foreach { case (dir, contentType) =>
    get("/public/" + dir + "/:id") { request =>
      val file = TempFile.fromResourcePath("/public/" + dir + "/" + request.params("id"))
      if (file.exists()) {
        val resp = new FinatraResponse
        resp.status = 200
        resp.binBody = Some(Files.readBytes(file))
        resp.headers += ("Content-Type" -> contentType)
        resp.layout = None
        resp.build
      } else {
        response(status = 404, body = "Not Found")
      }
    }
  }
}

