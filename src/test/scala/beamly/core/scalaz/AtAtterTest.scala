package beamly.core.scalaz

import org.specs2._
import scalaz.@@
import scalaz.Tags.Conjunction

class AtAtterTest extends mutable.Specification {
  def typed[T](t: => T) = ok  // Inspired from shapeless

  "@@" >> {
    "true @@ Conjunction"  ! typed[Boolean @@ Conjunction](true.@@[Conjunction])
    "false @@ Conjunction" ! typed[Boolean @@ Conjunction](false.@@[Conjunction])
  }
  "tagged" >> {
    "true @@ Conjunction"  ! typed[Boolean @@ Conjunction](true.tagged[Conjunction])
    "false @@ Conjunction" ! typed[Boolean @@ Conjunction](false.tagged[Conjunction])
  }
}
