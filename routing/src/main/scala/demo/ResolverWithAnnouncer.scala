package demo

import com.twitter.finagle._
import com.twitter.util.{Future, Var}

import java.net.InetSocketAddress
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}

class ResolverWithAnnouncer extends Resolver with Announcer {
  private val services: ConcurrentMap[String, InetSocketAddress] = new ConcurrentHashMap()
  override val scheme: String                                    = "simple"

  override def bind(arg: String): Var[Addr] =
    Option(services.get(arg)) match {
      case Some(address) => Var.value(Addr.Bound(Address(address)))
      case None          => Var.value(Addr.Neg)
    }

  override def announce(addr: InetSocketAddress, name: String): Future[Announcement] = Future {
    println(s"Register: $name => $addr")
    services.put(name, addr)

    () =>
      Future {
        services.remove(name)
      }
  }
}

object ResolverWithAnnouncer {
  object Namer {
    private[this] def resolve(scheme: String, path: Seq[String]): Var[Addr] =
      Resolver.eval(s"$scheme!${path.mkString("/")}") match {
        case Name.Bound(va) => va
        case n: Name.Path =>
          Var.value(Addr.Failed(new IllegalStateException(s"Resolver returned an unbound name: $n.")))
      }

    def unapply(path: Path): Option[(Var[Addr], Path)] = path match {
      case Path.Utf8(scheme, residual @ _*) => Some((resolve(scheme, residual), Path.empty))
      case _                                => None
    }
  }
}
