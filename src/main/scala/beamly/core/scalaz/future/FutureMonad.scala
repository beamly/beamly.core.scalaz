package beamly.core.scalaz.future

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scalaz._
import beamly.core.lang._

final class FutureMonad(implicit executionContext: ExecutionContext) extends Monad[Future] with Each[Future] with Zip[Future] with Unzip[Future] {
  def point[A](a: => A): Future[A] = Try(a).future

  def bind[A, B](future: Future[A])(f: (A) => Future[B]): Future[B] = future flatMap f

  override def map[A, B](future: Future[A])(f: A => B) = future map f

  def each[A](future: Future[A])(f: (A) => Unit) { future foreach f }

  def zip[A, B](fa: => Future[A], fb: => Future[B]): Future[(A, B)] = for (a <- fa; b <- fb) yield (a, b)

  def unzip[A, B](future: Future[(A, B)]): (Future[A], Future[B]) = (future map (_._1), future map (_._2))

  override def apply2[A, B, C](fa: => Future[A], fb: => Future[B])(f: (A, B) => C): Future[C] =
    for (a <- fa; b <- fb) yield f(a, b)
}

final class SeqFutureMonoid[A](implicit executionContext: ExecutionContext, monoidA: Monoid[A]) extends Monoid[Future[A]] {

  def append(f1: Future[A], f2: => Future[A]): Future[A] = for (a1 <- f1; a2 <- f2) yield (monoidA.append(a1,a2))

  def zero: Future[A] = Future successful monoidA.zero

}

final class ParFutureMonoid[A](implicit executionContext: ExecutionContext, monoidA: Monoid[A]) extends Monoid[Future[A]] {

  def append(f1: Future[A], f2: => Future[A]): Future[A] = for (a2 <- f2; a1 <- f1) yield (monoidA.append(a1,a2))

  def zero: Future[A] = Future successful monoidA.zero

}
