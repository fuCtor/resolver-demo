package demo

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle._
import com.twitter.util.{Duration, Future, Timer, Var}

import scala.util.Random

class SimpleResolverV2(negProbability: Map[String, Double])(implicit timer: Timer) extends Resolver {
  override val scheme: String = "simple"

  def build(label: String, service: Service[Request, Response]): Var[Addr] = Var.async[Addr](Addr.Pending) { updater =>
    timer.schedule(Duration.fromSeconds(2 + Random.nextInt(5))) {
      val neg = negProbability.get(label).exists(_ >= Random.nextDouble())

      println(s"\tUpdater for $label neg=$neg")

      val addr =
        if (neg) Addr.Neg
        else Addr.Bound(Address.ServiceFactory(ServiceFactory.const(service)))
      updater.update(addr)
    }
  }

  override def bind(arg: String): Var[Addr] = {
    val simpleService = Service.mk[Request, Response] { req =>
      println(s"\t\tRequest on: $arg => ${req.contentString}")
      Future.value(Response(req))
    }

    arg.split('/').headOption match {
      case Some(label) => build(label, simpleService)
      case _           => Var.value(Addr.Bound(Address.ServiceFactory(ServiceFactory.const(simpleService))))
    }
  }
}

object SimpleResolverV2 {
  def apply(negProbability: Map[String, Double])(implicit timer: Timer): SimpleResolverV2 = new SimpleResolverV2(
    negProbability
  )

  object Namer {
    private[this] def resolve(scheme: String, path: Seq[String]): Var[Addr] =
      Resolver.eval(s"$scheme!${path.mkString("/")}") match {
        case Name.Bound(va) => va
        case n: Name.Path =>
          Var.value(Addr.Failed(new IllegalStateException(s"DemoResolver returned an unbound name: $n.")))
      }

    def unapply(path: Path): Option[(Var[Addr], Path)] = path match {
      case Path.Utf8(scheme, residual @ _*) => Some((resolve(scheme, residual), Path.empty))
      case _                                => None
    }
  }
}
