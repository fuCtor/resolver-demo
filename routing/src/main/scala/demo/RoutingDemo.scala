package demo

import com.twitter.app.LoadService.Binding
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle._
import com.twitter.util.{Await, Future}

object RoutingDemo extends com.twitter.app.App {
  val simpleResolver = new ResolverWithAnnouncer

  protected[this] override val loadServiceBindings: Seq[Binding[_]] =
    Seq(new Binding(classOf[Resolver], Seq(simpleResolver)), new Binding(classOf[Announcer], Seq(simpleResolver)))

  def logService(name: String): SimpleFilter[Request, Response] =
    (request: Request, service: Service[Request, Response]) => {
      val headers = request.headerMap.toMap.filter { case (k, v) =>
        (k.startsWith("X-") && !k.startsWith("X-B3")) ||
          (k.startsWith("x-") && !k.startsWith("x-b3") && !k.startsWith("x-http2"))
      }
      println(s"Request [$name]: ${headers.mkString(",")}")
      service(request)
    }

  private def serviceClient(dest: String) = Http.client.newService(dest)

  private def serveService(name: String, service: Service[Request, Response]) =
    closeOnExit(Http.server.serveAndAnnounce(s"simple!$name", "0.0.0.0:0", logService(name).andThen(service)))

  premain {
    val serviceC = Service.mk[Request, Response] { req =>
      Future.value(Response(req))
    }

    serveService("service_c", serviceC)
  }

  premain {
    val serviceCClient = serviceClient("/s/service_c")
    val serviceCProxy = Service.mk[Request, Response] { req =>
      req.headerMap.set("X-APP-VIA", "ServiceC:Proxy")

      Dtab.unwind {
        Dtab.local ++= Dtab.read("""
                                 |/s => /s#;
                                 |""".stripMargin)
        serviceCClient(req)
      }
    }

    serveService("service_c_proxy", serviceCProxy)
  }

  premain {
    val serviceB = {
      val serviceCClient = serviceClient("/s/service_c")
      Service.mk[Request, Response] { _ =>
        val req = Request()
        req.headerMap.set("X-APP-FROM", "ServiceB")
        serviceCClient(req)
      }
    }

    serveService("service_b", serviceB)
  }

  premain {
    val serviceA = {
      val serviceCClient = serviceClient("/s/service_c")
      val serviceBClient = serviceClient("/s/service_b")
      Service.mk[Request, Response] { _ =>
        val req = Request()
        req.headerMap.set("X-APP-FROM", "ServiceA")
        serviceBClient(req).flatMap(_ => serviceCClient(req))
      }
    }

    val dtabRewriter: SimpleFilter[Request, Response] = (request: Request, service: Service[Request, Response]) =>
      Dtab.unwind {
        if (request.getBooleanParam("rewrite", false)) {
          Dtab.local = Dtab.read("""
                                   |/s/service_c => /s/service_c_proxy;
                                   |""".stripMargin)
        }

        service(request)
      }

    serveService("service_a", dtabRewriter.andThen(serviceA))
  }

  premain {
    Dtab.base = Dtab.read("""
                            |/s## => /$/demo.Namer/simple;
                            |/s# => /s##;
                            |/s  => /s#;
                            |""".stripMargin)
  }

  def main(): Unit = {
    val serviceAClient = serviceClient("/s/service_a")

    println("Direct request:")

    val reqDirect = Request()
    reqDirect.headerMap.set("X-APP-FROM", "Client")
    Await.result(serviceAClient(reqDirect))

    println("---------------")
    println("Via proxy + Dtab.local:")

    Await.result {
      val reqDirect = Request("/?rewrite=true")
      reqDirect.headerMap.set("X-APP-FROM", "Client")
      serviceAClient(reqDirect)
    }
  }

}
