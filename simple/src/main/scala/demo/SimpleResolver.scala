package demo

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle._
import com.twitter.util.{Future, Var}

class SimpleResolver extends Resolver {
  override val scheme: String = "simple"

  override def bind(arg: String): Var[Addr] = {
    val simpleService = Service.mk[Request, Response] { req =>
      println(s"Simple Request [$arg]: ${req.contentString}")
      Future.value(Response(req))
    }
    Var.value(Addr.Bound(Address.ServiceFactory(ServiceFactory.const(simpleService))))
  }
}
