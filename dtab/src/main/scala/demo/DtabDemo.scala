package demo

import com.twitter.app.LoadService.Binding
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Dtab, Http, Resolver, Service}
import com.twitter.util.{Await, Duration, Future}

object DtabDemo extends com.twitter.app.App {
  import com.twitter.finagle.util.DefaultTimer.Implicit

  protected[this] override val loadServiceBindings: Seq[Binding[_]] =
    Seq(new Binding(classOf[Resolver], Seq(SimpleResolverV2(Map("stage1" -> 0.5, "stage2" -> 0.5)))))

  def makeRequest(client: Service[Request, Response], i: Int): Future[Unit] = Dtab
    .unwind {
      Dtab.local = Dtab.read("""
                                    |/s# => (/s##/stage1 & /s##/stage2);
        |""".stripMargin)
      val req = Request()
      req.contentString = i.toString

      client(req)
    }
    .transform(_ => Future.Unit)
    .delayed(Duration.fromMilliseconds(250))
    .flatMap(_ => makeRequest(client, i + 1))

  def main(): Unit = {
    val client = Http.client.newService("/s/api")
    Dtab.base = Dtab.read("""
                            |/s## => /$/demo.Namer/simple;
                            |/s# => /s##/prod;
                            |/s  => /s#;
                            |""".stripMargin)

    Await.result(makeRequest(client, 0))
  }

}
