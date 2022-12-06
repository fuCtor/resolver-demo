package demo

import com.twitter.app.LoadService.Binding
import com.twitter.finagle._
import com.twitter.finagle.util.DefaultTimer
import com.twitter.util.{Await, Future}

/*
Пример реализации Resolver, который будет брать хосты из файла.
Результат будем выводить в консоль.
 */

object FileResolverDemo extends com.twitter.app.App {
  import DefaultTimer.Implicit

  protected[this] override val loadServiceBindings: Seq[Binding[_]] =
    Seq(new Binding(classOf[Resolver], Seq(new FileResolver)))

  def main(): Unit = {
    // Запрашиваем наши хосты
    Resolver.eval("file!./hosts.yaml#gitlab") match {
      case Name.Bound(addrs) => addrs.changes.respond(println)
      case Name.Path(_)      => ()
    }

    Await.result(Future.never)
  }

}
