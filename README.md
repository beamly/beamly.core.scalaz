Scalaz extensions
=================

`FutureEither`
--------------
`FutureEither` is a specialised version of `scalaz.EitherT[Future, A, B]` which allows simple composition of
`Future[A \/ B]` operations.

Usage
-----
```scala
import scala.concurrent.Future
import com.zeebox.core.http.common.HttpStatus.{NotFound, Ok}
import com.zeebox.core.http.server.http.HttpResponse
import com.zeebox.core.scalaz.future.{FutureEither, \?/}

case class UserNotFound(userId: String)

class Example {
  def findFriends(userId: UserId): UserNotFound \?/ Seq[User] = ???

  def handleUserNotFound: UserNotFound => HttpResponse[Nothing] = {
    userNotFoundError => HttpResponse.failure(NotFound, s"User $userId not found")
  }

  def friendsResponse(userId: UserId): Future[HttpResponse[Seq[User]]] = {
    val response: FutureEither[HttpResponse[Nothing], HttpResponse[Seq[User]]] = for {
      friends <- findFriends(userId) leftMap handleUserNotFound
    } yield {
      HttpResponse(Ok, data = Some(friends))
    }
    response.union
  }
}
```
The steps above when calling `friendsResponse` are:
* call `findFriends` which returns either a user not found error or a list of friends
* the call to `leftMap` maps the left-hand result (ie: `UserNotFound`) to a 404 `HttpResponse`
* the yield returns the right-hand result which is the list of your friends, but wrapped in a 200 `HttpResponse`
* the entire for-comprehension thus returns `FutureEither[HttpResponse[Nothing], HttpResponse[Seq[User]]]`
* the `union` method flattens the `FutureEither[HttpResponse[Nothing], HttpResponse[Seq[User]]]` to a `Future[HttpResponse[Seq[User]]]`

Converting a regular `Future` to a `FutureEither`
-------------------------------------------------
```scala
def findUser(userId: UserId): Future[Option[User]]

findUser.toEither.leftMap {
    case ex: UserNotFoundException =>
        UserNotFound(userId)
}
```
