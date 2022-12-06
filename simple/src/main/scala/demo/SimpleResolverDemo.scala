package demo

import com.twitter.app.LoadService.Binding
import com.twitter.finagle.{Http, Resolver}
import com.twitter.finagle.http.Request
import com.twitter.util.Await

object SimpleResolverDemo extends com.twitter.app.App {

  protected[this] override val loadServiceBindings: Seq[Binding[_]] =
    Seq(new Binding(classOf[Resolver], Seq(new SimpleResolver)))

  def main(): Unit = {
    val client = Http.client.newService("simple!hello")

    val req = Request()
    req.contentString = "world"
    Await.result(client(req))
  }

}
