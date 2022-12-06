package demo

import com.twitter.app.LoadService.Binding
import com.twitter.finagle.{Http, Resolver, Service}
import com.twitter.finagle.http.{Request, Response}
import com.twitter.util.{Await, Duration, Future}

import java.time.Clock

object AsyncResolverDemo extends com.twitter.app.App {
  import com.twitter.finagle.util.DefaultTimer.Implicit

  protected[this] override val loadServiceBindings: Seq[Binding[_]] =
    Seq(new Binding(classOf[Resolver], Seq(new AsyncResolver(Clock.systemUTC()))))

  def makeRequest(client: Service[Request, Response], clock: Clock): Future[Unit] = {
    val req = Request()
    req.contentString = clock.instant().toString
    client(req).unit
  }

  def main(): Unit = {
    val client = Http.client.newService("async!hello")

    val f = (0 to 30).foldLeft(Future.Unit) { case (f, _) =>
      f.flatMap(_ => makeRequest(client, Clock.systemUTC()).delayed(Duration.fromSeconds(1)))
    }

    Await.result(f)
  }

}
