package demo

import com.twitter.finagle.{Addr, Name, NameTree, Path, Namer => TwNamer}
import com.twitter.util.{Activity, Event}

class Namer extends TwNamer {
  override def lookup(path: Path): Activity[NameTree[Name]] = path match {
    case ResolverWithAnnouncer.Namer(va, residual) =>
      val id = path.take(path.size - residual.size)
      val events: Event[Activity.State[NameTree[Name]]] = va.changes.map {
        case Addr.Bound(_, _) => Activity.Ok(NameTree.Leaf(Name.Bound(va, id, residual)))
        case Addr.Failed(exp) => Activity.Failed(exp)
        case Addr.Pending     => Activity.Pending
        case Addr.Neg         => Activity.Ok(NameTree.Neg)
      }.dedup

      Activity(events)
    case _ =>
      Activity.value(NameTree.Neg)
  }
}
