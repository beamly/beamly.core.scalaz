package com.zeebox.core.scalaz

import scala.language.higherKinds
import scala.concurrent.{Future, ExecutionContext}
import scalaz._
import scala.util.{Failure, Success, Try}
import beamly.core.lang._
import beamly.core.lang.future._

package object future {

  type \?/[+L, +R] = FutureEither[L, R]

  val \?/ = FutureEither

  implicit final class FutureScalazW[A](val underlying: Future[A]) extends AnyVal {
    @deprecated("Use 'toEither' instead", "0.31")
    def disjoin(implicit executor: ExecutionContext): Future[Throwable \/ A] = underlying mapTry {
      case Success(r) => \/-(r)
      case Failure(e) => -\/(e)
    }

    def toEither(implicit ec: ExecutionContext): FutureEither[Throwable, A] = FutureEither {
      underlying mapTry {
        case Success(r) => \/-(r)
        case Failure(e) => -\/(e)
      }
    }

    def toRight(implicit ec: ExecutionContext): FutureEither[Nothing, A] = FutureEither right underlying

    def toLeft(implicit ec: ExecutionContext): FutureEither[A, Nothing] = FutureEither left underlying
  }

  implicit def futureMonad(implicit executionContext: ExecutionContext) = new FutureMonad

  implicit def futureMonoid[A](implicit executionContext: ExecutionContext, monoidA: Monoid[A]) = new SeqFutureMonoid[A]

  implicit class FutureDisjunctionW[A](val underlying: Future[Throwable \/ A]) extends AnyVal {
    def join(implicit executor: ExecutionContext) = underlying flatMap (_.fold(Future.failed, Future.successful))
  }

  implicit class DisjunctionToFuture[A](val underlying: Throwable \/ A) extends AnyVal {
    def future: Future[A] = underlying fold (Future.failed, Future.successful)
  }

  implicit class MonadToFuture[M[_], A](val underlying: M[A]) extends AnyVal {

    def parFoldMap[B](f: A => Future[B])(implicit mb: Monoid[B], fm: Foldable[M], ec: ExecutionContext): Future[B] =
      fm.foldLeft(underlying, Future successful mb.zero)((fr, a) => for (b <- f(a); r <- fr) yield (mb append (r, b)))

    def seqFoldMap[B](f: A => Future[B])(implicit mb: Monoid[B], fm: Foldable[M], ec: ExecutionContext): Future[B] =
      fm.foldLeft(underlying, Future successful mb.zero)((fr, a) => for (r <- fr; b <- f(a)) yield (mb append (r, b)))

    def parFoldUnit(f: A => Future[Any])(implicit fm: Foldable[M], ec: ExecutionContext): Future[Unit] =
      fm.foldLeft(underlying, futureUnit)((fr, a) => for (_ <- f(a); _ <- fr) yield ())

    def seqFoldUnit(f: A => Future[Any])(implicit fm: Foldable[M], ec: ExecutionContext): Future[Unit] =
      fm.foldLeft(underlying, futureUnit)((fr, a) => for (_ <- fr; _ <- f(a)) yield ())

    /**
     * Traverse biased to the right side of \?/
     */
    def traverseR[L, R](f: A => (FutureEither[L, R]))(implicit tm: Traverse[M], ex: ExecutionContext): FutureEither[L, M[R]] =
      tm.traverse[({type l[a] = FutureEither[L, a]})#l, A, R](underlying)(f)(\?/.futureEitherMonad[L])

    def sequenceR[L, R](implicit tm: Traverse[M], ex: ExecutionContext, ev: M[A] <:< M[(FutureEither[L, R])]): FutureEither[L, M[R]] =
      tm.sequence[({type l[a] = FutureEither[L, a]})#l, R](ev(underlying))(\?/.futureEitherMonad[L])

    def traverseRight[L, R](f: A => (FutureEither[L, R]))(implicit tm: Traverse[M], ex: ExecutionContext): FutureEither[L, M[R]] = traverseR(f)(tm, ex)

    def sequenceRight[L, R](implicit tm: Traverse[M], ex: ExecutionContext, ev: M[A] <:< M[(FutureEither[L, R])]): FutureEither[L, M[R]] = sequenceR(tm, ex, ev)

  }

  implicit class MonadFutureToFuture[M[_], A](val underlying: M[Future[A]]) extends AnyVal {

    def parFoldMap()(implicit ma: Monoid[A], fm: Foldable[M], ec: ExecutionContext): Future[A] =
      fm.foldLeft(underlying, Future successful ma.zero)((fr, fa) => for (a <- fa; r <- fr) yield (ma append (r, a)))

    def seqFoldMap()(implicit ma: Monoid[A], fm: Foldable[M], ec: ExecutionContext): Future[A] =
      fm.foldLeft(underlying, Future successful ma.zero)((fr, fa) => for (r <- fr; a <- fa) yield (ma append (r, a)))

    def parFoldUnit()(implicit fm: Foldable[M], ec: ExecutionContext): Future[Unit] =
      fm.foldLeft(underlying, futureUnit)((fr, fa) => for (_ <- fa; _ <- fr) yield ())

    def seqFoldUnit()(implicit fm: Foldable[M], ec: ExecutionContext): Future[Unit] =
      fm.foldLeft(underlying, futureUnit)((fr, fa) => for (_ <- fr; _ <- fa) yield ())

  }

  implicit class TraversableOnceToFuture[A](val underlying: TraversableOnce[A]) extends AnyVal {

    def parFoldMap[B](f: A => Future[B])(implicit mb: Monoid[B], ec: ExecutionContext): Future[B] =
      (Future.successful(mb.zero) /: underlying)((fr, a) => for (b <- f(a); r <- fr) yield (mb append (r, b)))

    def seqFoldMap[B](f: A => Future[B])(implicit mb: Monoid[B], ec: ExecutionContext): Future[B] =
      (Future.successful(mb.zero) /: underlying)((fr, a) => for (r <- fr; b <- f(a)) yield (mb append (r, b)))

    def parFoldUnit(f: A => Future[Any])(implicit ec: ExecutionContext): Future[Unit] =
      (futureUnit /: underlying)((fr, a) => for (_ <- f(a); _ <- fr) yield ())

    def seqFoldUnit(f: A => Future[Any])(implicit ec: ExecutionContext): Future[Unit] =
      (futureUnit /: underlying)((fr, a) => for (_ <- fr; _ <- f(a)) yield ())

  }

  implicit class TraversableOnceFutureToFuture[A](val underlying: TraversableOnce[Future[A]]) extends AnyVal {

    def parFoldMap()(implicit ma: Monoid[A], ec: ExecutionContext): Future[A] =
      (Future.successful(ma.zero) /: underlying)((fr, fa) => for (a <- fa; r <- fr) yield (ma append (r, a)))

    def seqFoldMap()(implicit ma: Monoid[A], ec: ExecutionContext): Future[A] =
      (Future.successful(ma.zero) /: underlying)((fr, fa) => for (r <- fr; a <- fa) yield (ma append (r, a)))

    def parFoldUnit()(implicit ec: ExecutionContext): Future[Unit] =
      (futureUnit /: underlying)((fr, fa) => for (_ <- fa; _ <- fr) yield ())

    def seqFoldUnit()(implicit ec: ExecutionContext): Future[Unit] =
      (futureUnit /: underlying)((fr, fa) => for (_ <- fr; _ <- fa) yield ())

  }

}
