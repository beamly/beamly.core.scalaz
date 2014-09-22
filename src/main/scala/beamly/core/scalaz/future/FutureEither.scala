package beamly.core.scalaz.future

import scala.language.higherKinds
import scala.concurrent.{Await, ExecutionContext, Future}
import scalaz._
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.Try
import beamly.core.lang._

sealed trait FutureEitherApplyFactory[F[+_]] {
  def apply[L, R](in: => F[L \/ R]): FutureEither[L, R]
}

object FutureEitherApplyFactory {
  implicit object FutureApplyFactory extends FutureEitherApplyFactory[Future] {
    @inline
    def apply[L, R](in: => Future[L \/ R]) = new FutureEither(try in catch { case e if NonFatal(e) => Future failed e })
  }
}


/** A specialised version of scalaz.EitherT[Future, A, B] to cut down on the verbosity of the type. */
final class FutureEither[+L, +R](val future: Future[L \/ R]) {

  def map[RR](f: R => RR)(implicit ec: ExecutionContext): FutureEither[L, RR] = FutureEither(future map (_ map f))

  def flatMap[LL >: L, RR](f: R => FutureEither[LL, RR])(implicit ec: ExecutionContext): FutureEither[LL, RR] = FutureEither {
    future flatMap {
      case \/-(r)        => f(r).future
      case left @ -\/(_) => Future successful left
    }
  }

  def >>=[LL >: L, RR](f: R => FutureEither[LL, RR])(implicit ec: ExecutionContext): FutureEither[LL, RR] = flatMap(f)

  def >>[LL >: L, RR](f: => FutureEither[LL, RR])(implicit ec: ExecutionContext): FutureEither[LL, RR] = flatMap(_ => f)

  def valueOrThrow(implicit ev: L <:< Throwable, ec: ExecutionContext): Future[R] = future map (_ valueOr (throw _))

  def leftMap[LL](f: L => LL)(implicit ec: ExecutionContext): FutureEither[LL, R] = FutureEither(future map (_ leftMap f))

  def leftFlatMap[LL, RR >: R](f: L => FutureEither[LL, RR])(implicit ec: ExecutionContext): FutureEither[LL, RR] = FutureEither {
    future flatMap {
      case -\/(l)        => f(l).future
      case right @ \/-(_) => Future successful right
    }
  }

  def recover[RR >: R](pf: PartialFunction[L, RR])(implicit ec: ExecutionContext): FutureEither[L, RR] = FutureEither {
    future map {
      case -\/(l) => pf andThen (\/-(_)) applyOrElse (l, (x: L) => -\/(x))
      case r      => r
    }
  }

  def flatRecover[LL >: L, RR >: R](pf: PartialFunction[L, FutureEither[LL, RR]])(implicit ec: ExecutionContext): FutureEither[LL, RR] = FutureEither {
    future flatMap {
      case -\/(l) => (pf applyOrElse (l, (x: L) => FutureEither(-\/(x)))).future
      case r      => Future successful r
    }
  }

  // only for testing!
  def get(): L \/ R = get(5.seconds)

  // only for testing!
  def get(duration: Duration): L \/ R = Await result (future, duration)

  def getRight(): R = getRight(5.seconds)

  def getRight(duration: Duration): R = get(duration) valueOr (l => throw new NoSuchElementException(s"Contains: $l"))

  def getLeft(): L = getLeft(5.seconds)

  def getLeft(duration: Duration): L = get(duration).swap valueOr (r => throw new NoSuchElementException(s"Contains: $r"))

  // copied scalaz methods

  /** If this disjunction is right, return the given X value, otherwise, return the X value given to the return value. */
  def :?>>[X](right: => X)(implicit ec: ExecutionContext): \?/.Switching[L, R, X] = new \?/.Switching(future, right)

  def fold[X](l: L => X, r: R => X)(implicit ec: ExecutionContext): Future[X] = future map (_.fold(l, r))

  def flatFold[LL, RR](onLeft: L => FutureEither[LL, RR], onRight: R => FutureEither[LL, RR])(implicit ec: ExecutionContext): FutureEither[LL, RR] =
    FutureEither {
      future flatMap {
        case -\/(l) => onLeft(l).future
        case \/-(r) => onRight(r).future
      }
    }

  def union[X >: R](implicit leftEv: L <:< X, ec: ExecutionContext): Future[X] = valueOr[X](identity[L])

  /** Return `true` if this disjunction is left. */
  def isLeft(implicit ec: ExecutionContext): Future[Boolean] = future map (_.isLeft)

  /** Return `true` if this disjunction is right. */
  def isRight(implicit ec: ExecutionContext): Future[Boolean] = future map (_.isRight)

  /** Flip the left/right values in this disjunction. Alias for `swap` */
  def swap(implicit ec: ExecutionContext): FutureEither[R, L] = FutureEither(future map (_.swap))

  /** Flip the left/right values in this disjunction. Alias for `unary_~` */
  def unary_~(implicit ec: ExecutionContext): FutureEither[R, L] = swap

  /** Run the given function on this swapped value. Alias for `~` */
  def swapped[LL, RR](k: (R \/ L) => (RR \/ LL))(implicit ec: ExecutionContext): FutureEither[LL, RR] = FutureEither(future map (_ swapped k))

  /** Run the given function on this swapped value. Alias for `swapped` */
  def ~[LL, RR](k: (R \/ L) => (RR \/ LL))(implicit ec: ExecutionContext): FutureEither[LL, RR] = swapped(k)

  /** Binary functor map on this disjunction. */
  def bimap[LL, RR](f: L => LL, g: R => RR)(implicit ec: ExecutionContext): FutureEither[LL, RR] = FutureEither(future map (_ bimap (f, g)))

  /** Run the side-effect on the right of this disjunction. */
  def foreach(f: R => Unit)(implicit ec: ExecutionContext) { future foreach (_ foreach f) }

  /** Apply a function in the environment of the right of this disjunction. */
  def ap[LL >: L, RR](f: => LL \?/ (R => RR))(implicit ec: ExecutionContext): FutureEither[LL, RR] = flatMap[LL, RR](r => f.map(_(r)))

  /** Filter on the right of this disjunction. */
  def filter[LL >: L: Monoid](p: R => Boolean)(implicit ec: ExecutionContext): FutureEither[LL, R] = FutureEither(future map (_.filter[LL](p)))

  /** Return `true` if this disjunction is a right value satisfying the given predicate. */
  def exists(f: R => Boolean)(implicit ec: ExecutionContext): Future[Boolean] = future map (_ exists f)

  /** Return `true` if this disjunction is a left value or the right value satisfies the given predicate. */
  def forall(f: R => Boolean)(implicit ec: ExecutionContext): Future[Boolean] = future map (_ forall f)

  /** Return an empty list or list with one element on the right of this disjunction. */
  def toList(implicit ec: ExecutionContext): Future[List[R]] = future map (_.toList)

  /** Return an empty stream or stream with one element on the right of this disjunction. */
  def toStream(implicit ec: ExecutionContext): Future[Stream[R]] = future map (_.toStream)

  /** Return an empty option or option with one element on the right of this disjunction. Useful to sweep errors under the carpet. */
  def toOption(implicit ec: ExecutionContext): Future[Option[R]] = future map (_.toOption)

  /** Convert to a core `scala.Either` at your own peril. */
  def toEither(implicit ec: ExecutionContext): Future[Either[L, R]] = future map (_.toEither)

  /** Return the right value of this disjunction or the given default if left. Alias for `|` */
  def getOrElse[RR >: R](default: => RR)(implicit ec: ExecutionContext): Future[RR] = future map (_ getOrElse default)

  /** Return the right value of this disjunction or the given default if left. Alias for `getOrElse` */
  def |[RR >: R](default: => RR)(implicit ec: ExecutionContext): Future[RR] = getOrElse(default)

  /** Return the right value of this disjunction or run the given function on the left. */
  def valueOr[RR >: R](x: L => RR)(implicit ec: ExecutionContext): Future[RR] = future map (_ valueOr x)

  /** Return this if it is a right, otherwise, return the given value. Alias for `|||` */
  def orElse[LL >: L, RR >: R](x: => FutureEither[LL, RR])(implicit ec: ExecutionContext): FutureEither[LL, RR] =
    FutureEither {
      future flatMap {
        case -\/(_) => x.future
        case \/-(_) => future
      }
    }

  /** Return this if it is a right, otherwise, return the given value. Alias for `orElse` */
  def |||[LL >: L, RR >: R](x: => FutureEither[LL, RR])(implicit ec: ExecutionContext): FutureEither[LL, RR] = orElse(x)

  /**
   * Sums up values inside disjunction, if both are left or right. Returns first left otherwise.
   * {{{
   * \/-(v1) +++ \/-(v2) → \/-(v1 + v2)
   * \/-(v1) +++ -\/(v2) → -\/(v2)
   * -\/(v1) +++ \/-(v2) → -\/(v1)
   * -\/(v1) +++ -\/(v2) → -\/(v1 + v2)
   * }}}
   */
  def +++[LL >: L: Semigroup, RR >: R: Semigroup](x: => FutureEither[LL, RR])(implicit ec: ExecutionContext): FutureEither[LL, RR] = FutureEither(for (f1 <- future; f2 <- x.future) yield (f1 +++ f2))

  /** Ensures that the right value of this disjunction satisfies the given predicate, or returns left with the given value. */
  def ensure[LL >: L](onLeft: => LL)(f: R => Boolean)(implicit ec: ExecutionContext): FutureEither[LL, R] = FutureEither(future map (_.ensure(onLeft)(f)))

  /** Compare two disjunction values for equality. */
  def ===[LL >: L: Equal, RR >: R: Equal](x: FutureEither[LL, RR])(implicit ec: ExecutionContext): Future[Boolean] = for (f1 <- future; f2 <- x.future) yield (f1 === f2)

  /** Compare two disjunction values for ordering. */
  def compare[LL >: L: Order, RR >: R: Order](x: FutureEither[LL, RR])(implicit ec: ExecutionContext): Future[Ordering] = for (f1 <- future; f2 <- x.future) yield (f1 compare f2)

  /** Show for a disjunction value. */
  def show[LL >: L: Show, RR >: R: Show](implicit ec: ExecutionContext): Future[Cord] = future map (_.show[LL, RR])

  /** Convert to a validation. */
  def validation(implicit ec: ExecutionContext): Future[Validation[L, R]] = future map (_.validation)

  /** Run a validation function and back to disjunction again. */
  def validationed[LL, RR](k: Validation[L, R] => Validation[LL, RR])(implicit ec: ExecutionContext): FutureEither[LL, RR] = FutureEither(future map (_ validationed k))

}

object FutureEither {

  @inline
  def apply[L, R, F[+_]](in: => F[L \/ R])(implicit factory: FutureEitherApplyFactory[F]): FutureEither[L, R] = factory(in)

  @inline
  def apply[L, R](either: => L \/ R): FutureEither[L, R] = apply(Future successful either)

  @inline
  def apply[R](block: => R)(implicit ec: ExecutionContext): FutureEither[Throwable, R] =
    new FutureEither(Future successful (try \/-(block) catch { case e if NonFatal(e) => -\/(e) }))

  @inline
  def left[L](future: => Future[L])(implicit ec: ExecutionContext): FutureEither[L, Nothing] = apply(future map (\/ left _))

  @inline
  def right[R](future: => Future[R])(implicit ec: ExecutionContext): FutureEither[Nothing, R] = apply(future map (\/ right _))

  @inline
  def left[L](value: => L): FutureEither[L, Nothing] = apply(\/ left value)

  @inline
  def right[R](value: => R): FutureEither[Nothing, R] = apply(\/ right value)

  class Switching[+L, +R, X](future: Future[L \/ R], r: => X) {
    def <<?:(left: => X)(implicit ec: ExecutionContext): Future[X] =
      future map {
        case -\/(_) => left
        case \/-(_) => r
      }
  }

  implicit def futureEitherMonad[L](implicit ec: ExecutionContext) = new FutureEitherMonad[L]

  final class FutureEitherMonad[L](implicit ec: ExecutionContext) extends Monad[({type l[a] = FutureEither[L, a]})#l] with Plus[({type l[a] = FutureEither[L, a]})#l] with Each[({type l[a] = FutureEither[L, a]})#l] with Zip[({type l[a] = FutureEither[L, a]})#l] with Unzip[({type l[a] = FutureEither[L, a]})#l] {
    def bind[A, B](fa: FutureEither[L, A])(f: A => FutureEither[L, B]) = fa flatMap f

    override def map[A, B](fa: FutureEither[L, A])(f: A => B) = fa map f

    def point[A](a: => A): FutureEither[Nothing, A] = FutureEither(Try(\/-(a)).future)

    def plus[A](a: FutureEither[L, A], b: => FutureEither[L, A]) = a orElse b

    def each[A](fa: FutureEither[L, A])(f: (A) => Unit) { fa foreach f }

    def zip[A, B](fa: => FutureEither[L, A], fb: => FutureEither[L, B]): FutureEither[L, (A, B)] = for (a <- fa; b <- fb) yield (a, b)

    def unzip[A, B](fab: L \?/ (A, B)): (FutureEither[L, A], FutureEither[L, B]) = (fab map (_._1), fab map (_._2))

    override def apply2[A, B, C](fa: => FutureEither[L, A], fb: => FutureEither[L, B])(f: (A, B) => C): FutureEither[L, C] =
      for (a <- fa; b <- fb) yield f(a, b)

  }

  implicit def futureEitherMonoid[L: Semigroup, R: Monoid](implicit ec: ExecutionContext) = new FutureEitherMonoid[L, R]()

  final class FutureEitherMonoid[L, R](implicit semigroupL: Semigroup[L], monoidR: Monoid[R], ec: ExecutionContext) extends Monoid[FutureEither[L, R]] {
    def append(f1: FutureEither[L, R], f2: => FutureEither[L, R]) = FutureEither(f1.fold(l1 => \?/ left (f2.fold(l2 => semigroupL.append(l1, l2), r2 => l1)), r1 => f2.map(r2 => monoidR.append(r1, r2))) flatMap (_.future))

    def zero = FutureEither(\/-(monoidR.zero))
  }

}
