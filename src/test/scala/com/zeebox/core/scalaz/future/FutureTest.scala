package com.zeebox.core.scalaz.future

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import beamly.core.lang.future._
import org.specs2._
import scalaz.std.list._
import scalaz.\/
import org.specs2.execute.{Failure, AsResult}
import scala.concurrent.duration.Duration

class FutureTest extends mutable.Specification {

  implicit def futureAsResult[T: AsResult]: AsResult[Future[T]] = new AsResult[Future[T]] {
    def asResult(t: => Future[T]) = AsResult[T](t.get(Duration(10, "seconds")))
  }

  implicit def futureDisjunctionAsResult[L, R: AsResult]: AsResult[L \?/ R] = new AsResult[L \?/ R] {
    def asResult(t: => L \?/ R) = t.future.get(Duration(10, "seconds")) map (AsResult[R](_)) valueOr (f => Failure("Future contains failure: " + f.toString))
  }

  "disjoin" >> {
    "Right" ! {
      for {
        right <- Future("foo").disjoin.join
      } yield {
        right must_== "foo"
      }
    }
    "Left" ! {
      val future = Future {
        sys error "expected"
        "foo"
      }
      for {
        left <- future.disjoin.join.failed
      } yield {
        left.getMessage must_== "expected"
      }
    }
  }

  "toEither" >> {
    "Right" ! {
      for {
        right <- Future("foo").toEither.valueOrThrow
      } yield {
        right must_== "foo"
      }
    }
    "Left" ! {
      val future = Future {
        sys error "expected"
        "foo"
      }
      for {
        left <- future.toEither.valueOrThrow.failed
      } yield {
        left.getMessage must_== "expected"
      }
    }
  }

  "\\?/" >> {

    "traverseR and sequenceR" ! {

      val _list2 = List(1,2,3) map (\?/ right _)

      for {
        list1 <- List(1,2,3) traverseR (\?/ right _)
        list2 <- _list2.sequenceR
      } yield {
        list1 must_== List(1,2,3)
        list2 must_== List(1,2,3)
      }
    }

    "flatFold" ! {
      def process(z: String \?/ Int) =
        z flatFold (l => \?/ right l.length, {
          case 2 => \?/ left None
          case 3 => \?/ right 3
          case _ => \?/ left Some("bar")
        })

      process(\?/ right 3).get() must_== (\/ right 3)
      process(\?/ right 2).get() must_== (\/ left None)
      process(\?/ right 1).get() must_== (\/ left Some("bar"))
      process(\?/ left "foo").get() must_== (\/ right 3)
    }

    "union" ! {
      Future.successful(Seq("success")).toEither.leftMap(_ => Seq.empty[Nothing]).union.get ==== Seq("success")
      Future.failed[Seq[String]](new Exception("failure")).toEither.leftMap(_ => Seq.empty[Nothing]).union.get ==== Seq.empty[String]
    }

  }
}
