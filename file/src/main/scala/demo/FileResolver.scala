package demo

import com.twitter.finagle.addr.WeightedAddress
import com.twitter.finagle.{Addr, Address, Resolver}
import com.twitter.util.{Closable, Duration, Future, FuturePool, Timer, Updatable, Var}
import io.circe.generic.JsonCodec

import java.io.File
import java.net.{InetSocketAddress, URI}
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import scala.util.{Failure, Success, Try}
import io.circe.{Decoder, yaml}

class FileResolver(implicit timer: Timer) extends Resolver {
  override val scheme: String = "file"

  private def processTargets(targets: List[FileResolver.TargetHost]): Addr = {
    val addresses = targets.map { host =>
      val address = Address.Inet(host.address, Addr.Metadata(host.meta.getOrElse(Map.empty).toSeq: _*))
      host.weight.fold[Address](address)(WeightedAddress(address, _))
    }
    Addr.Bound(addresses: _*)
  }

  def watchForFile(targetLabel: String, file: File, updater: Updatable[Addr]): Closable = {

    def load(lastChanged: Option[Long]): Future[Unit] = FuturePool
      .interruptibleUnboundedPool {
        val lastModified = file.lastModified()
        lastChanged match {
          case Some(`lastModified`) => ()
          case _                    => updater.update(loadFromFile(targetLabel, file))
        }
        lastModified
      }
      .flatMap(time =>
        Future.Unit
          .delayed(Duration.fromSeconds(5))
          .flatMap(_ => load(Some(time)))
      )

    val f = load(None)

    Closable.make(_ => Future.value(f.raise(new InterruptedException())))
  }

  private def loadFromFile(targetLabel: String, file: File): Addr = {
    val result = if (file.exists()) {
      val bytes      = Files.readAllBytes(file.toPath)
      val raw        = new String(bytes, StandardCharsets.UTF_8)
      val jsonResult = yaml.parser.parse(raw)
      jsonResult
        .flatMap(_.as[Map[String, List[FileResolver.TargetHost]]])
        .map(_.find(_._1 == targetLabel))
    } else {
      Left(new FileResolver.FileNotFound(file))
    }

    // Тут все аналогично, уведомляем систему об ошибке, либо о том что не смогли ничего найти
    result match {
      case Left(err)                 => Addr.Failed(err)
      case Right(None)               => Addr.Neg
      case Right(Some((_, targets))) => processTargets(targets)
    }
  }

  private def getFile(fileUri: URI) =
    Try {
      val file  = new File(fileUri.getPath)
      val label = Option(fileUri.getFragment).getOrElse("")
      (label, file)
    }.toEither

  override def bind(arg: String): Var[Addr] = Try(new URI(arg)) match {
    case Failure(exception) => Var.value(Addr.Failed(exception))
    case Success(fileUri)   =>
      // Создаем Var, который будем обновлять вручную, начинаем с ожидания
      Var.async[Addr](Addr.Pending) { updater =>
        getFile(fileUri) match {
          case Left(err) =>
            // Выставляем ошибку, чтоб можно было в балансировщике это отработать и не ждать уже ничего
            updater.update(Addr.Failed(err))
            Closable.nop
          case Right((label, file)) if file.exists() =>
            // Загружаем хосты и подписываемся на обновления
            watchForFile(label, file, updater)
          case Right(_) =>
            // Сообщаем, что у нас ничего нет и можно идти дальше
            updater.update(Addr.Neg)
            Closable.nop
        }
      }
  }
}

object FileResolver {
  import cats.syntax.either._
  sealed abstract class Error(msg: String) extends Throwable(msg)

  class FileNotFound(file: File)     extends Error(s"File ${file.toPath.toString} not found")
  class LabelNotFound(label: String) extends Error(s"Label $label not found")

  object IntValue {
    def unapply(arg: String): Option[Int] = Try(arg.toInt).toOption
  }

  implicit val inetSocketAddressDecoder: Decoder[InetSocketAddress] = Decoder.decodeString.emap { value =>
    value.split(':') match {
      case Array(host, IntValue(port)) => InetSocketAddress.createUnresolved(host, port).asRight[String]
      case _                           => s"$value is not address".asLeft[InetSocketAddress]
    }
  }

  @JsonCodec(decodeOnly = true)
  case class TargetHost(address: InetSocketAddress, weight: Option[Double], meta: Option[Map[String, String]])

}
