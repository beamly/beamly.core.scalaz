package beamly.core.scalaz

object `package` {
  @inline implicit final class AtAtter[A](val any: A) extends AnyVal {
    @inline def @@[B] = scalaz.Tag[A, B](any)
    @inline def tagged[B] = @@[B]
  }
}
