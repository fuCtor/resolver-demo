package demo

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle._
import com.twitter.util.{Duration, Future, Timer, Var}

import java.time.Clock

class AsyncResolver(clock: Clock)(implicit timer: Timer) extends Resolver {
  override val scheme: String = "async"

  override def bind(arg: String): Var[Addr] = Var.async[Addr](Addr.Pending) { updater =>
    timer.schedule(Duration.fromSeconds(2)) {
      val created = clock.instant()
      val simpleService = Service.mk[Request, Response] { req =>
        println(s"[$created] Request [$arg]: ${req.contentString}")
        Future.value(Response(req))
      }
      val addr = Addr.Bound(Address.ServiceFactory(ServiceFactory.const(simpleService)))
      println("Update addr")
      updater.update(addr)
    }
  }
}
