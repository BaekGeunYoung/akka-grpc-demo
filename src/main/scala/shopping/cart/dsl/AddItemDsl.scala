package shopping.cart.dsl

import cats.free.Free
import cats.arrow.FunctionK
import cats.{Id, ~>}
import scala.collection.mutable
import cats.syntax.apply
import scala.concurrent.Future
import com.fasterxml.jackson.module.scala.deser.overrides
import cats.implicits.catsStdInstancesForFuture
import scala.concurrent.ExecutionContext
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import cats.Monad
import cats.implicits
import zio.*

/**
 * GrpcService
 */ 

trait GrpcService {
  def one(param: Int): Future[String]
  def two(param: Int): Future[String]
  def three(param: Int): Future[String]
}

class MockGrpcService extends GrpcService {
  override def one(param: Int): Future[String] = if param >= 10 then Future.successful("A") else Future.successful("B")
  override def two(param: Int): Future[String] = if param >= 10 then Future.successful("C") else Future.successful("D")
  override def three(param: Int): Future[String] = if param >= 10 then Future.successful("E") else Future.successful("F")
}

/**
 * Result and Event
 */
case class Result(value: Int)

sealed trait Event

object EventOne extends Event
object EventTwo extends Event
object EventThree extends Event
object EventFour extends Event
object EventFive extends Event
object EventSix extends Event
object EventSeven extends Event


/**
 * create Program & Interpreter using Free Monad
 */

sealed trait AddItemDslA[A]

case class CallExternalOne(param: Int) extends AddItemDslA[String]
case class CallExternalTwo(param: Int) extends AddItemDslA[String]
case class CallExternalThree(param: Int) extends AddItemDslA[String]


type AddItemDsl[A] = Free[AddItemDslA, A]

import cats.free.Free.liftF

def callExternalOne(param: Int): AddItemDsl[String] =
  liftF[AddItemDslA, String](CallExternalOne(param))

def callExternalTwo(param: Int): AddItemDsl[String] =
  liftF[AddItemDslA, String](CallExternalTwo(param))

def callExternalThree(param: Int): AddItemDsl[String] =
  liftF[AddItemDslA, String](CallExternalThree(param))


def addItemProgram(param1: Int, param2: Int, param3: Int): AddItemDsl[(Result, List[Event])] = {
  def getResultViaSecond(second: String) = second match {
      case "C" => (Result(10), List(EventOne, EventTwo, EventThree))
      case "D" => (Result(20), List(EventOne, EventTwo, EventFour))
      case "E" => (Result(30), List(EventOne, EventFive, EventSix))
      case "F" => (Result(40), List(EventOne, EventFive, EventSeven))
      case _ => throw Exception()
  }

  def callNextExternal(first: String, param2: Int, param3: Int) =
    if first == "A" then callExternalTwo(param2) else callExternalThree(param3)

  for {
    first <- callExternalOne(param1)
    second <- callNextExternal(first, param2, param3)
    result = getResultViaSecond(second)
  } yield result
}

def addItemInterpreter(grpcService: GrpcService): AddItemDslA ~> Future =
  new (AddItemDslA ~> Future) {
    def apply[A](fa: AddItemDslA[A]): Future[A] = 
      fa match {
        case CallExternalOne(param) => grpcService.one(param)
        case CallExternalTwo(param) => grpcService.two(param)
        case CallExternalThree(param) => grpcService.three(param)
      }
  }

implicit val ec: ExecutionContext = ExecutionContext.global

val result = addItemProgram(100, 200, 300).foldMap(addItemInterpreter(new MockGrpcService()))


/**
 * create Program & Interpreter using tagless final
 */
trait AddItemAlgebra[F[_]] {
  def callExternalOne(param: Int): F[String]
  def callExternalTwo(param: Int): F[String]
  def callExternalThree(param: Int): F[String]
}

class AddItemFutureInterpreter(
  grpcService: GrpcService
) extends AddItemAlgebra[Future] {
  override def callExternalOne(param: Int): Future[String] = grpcService.one(param)
  override def callExternalTwo(param: Int): Future[String] = grpcService.two(param)
  override def callExternalThree(param: Int): Future[String] = grpcService.three(param)
}

import cats.Monad.ops.toAllMonadOps

def addItemProgram2[F[_]: Monad](param1: Int, param2: Int, param3: Int)(algebra: AddItemAlgebra[F]): F[(Result, List[Event])] = {
  def getResultViaSecond(second: String) = second match {
      case "C" => (Result(10), List(EventOne, EventTwo, EventThree))
      case "D" => (Result(20), List(EventOne, EventTwo, EventFour))
      case "E" => (Result(30), List(EventOne, EventFive, EventSix))
      case "F" => (Result(40), List(EventOne, EventFive, EventSeven))
      case _ => throw Exception()
  }

  def callNextExternal(first: String, param2: Int, param3: Int): F[String] =
    if first == "A" then algebra.callExternalTwo(param2) else algebra.callExternalThree(param3)

  for {
    first <- algebra.callExternalOne(param1)
    second <- callNextExternal(first, param2, param3)
    result = getResultViaSecond(second)
  } yield result
}

val result2 = addItemProgram2(100, 200, 300)(AddItemFutureInterpreter(MockGrpcService()))


/**
 * using ZIO
 */

trait AddItemService {
  def callExternalOne(param: Int): Task[String]
  def callExternalTwo(param: Int): Task[String]
  def callExternalThree(param: Int): Task[String]
}

class AddItemServiceLive(grpcService: GrpcService) extends AddItemService {
  override def callExternalOne(param: Int): Task[String] = Task.fromFuture(_ => grpcService.one(param))

  override def callExternalTwo(param: Int): Task[String] = Task.fromFuture(_ => grpcService.two(param))

  override def callExternalThree(param: Int): Task[String] = Task.fromFuture(_ => grpcService.three(param))
}

object AddItemServiceLive {
  val layer: RLayer[Has[GrpcService], Has[AddItemService]] = (AddItemServiceLive(_)).toLayer
}

object GrpcServiceLive {
  val layer: ULayer[Has[GrpcService]] = (() => MockGrpcService()).toLayer
}

object AddItem {
  def callExternalOne(param: Int): RIO[Has[AddItemService], String] =
    ZIO.accessZIO(_.get.callExternalOne(param))

  def callExternalTwo(param: Int): RIO[Has[AddItemService], String] =
    ZIO.accessZIO(_.get.callExternalTwo(param))

  def callExternalThree(param: Int): RIO[Has[AddItemService], String] =
    ZIO.accessZIO(_.get.callExternalThree(param))
}

def addItemProgram3(param1: Int, param2: Int, param3: Int): RIO[Has[AddItemService], (Result, List[Event])] = {
  def getResultViaSecond(second: String) = second match {
      case "C" => (Result(10), List(EventOne, EventTwo, EventThree))
      case "D" => (Result(20), List(EventOne, EventTwo, EventFour))
      case "E" => (Result(30), List(EventOne, EventFive, EventSix))
      case "F" => (Result(40), List(EventOne, EventFive, EventSeven))
      case _ => throw Exception()
  }

  def callNextExternal(first: String, param2: Int, param3: Int): RIO[Has[AddItemService], String] =
    if first == "A" then AddItem.callExternalTwo(param2) else AddItem.callExternalThree(param3)

  for {
    first <- AddItem.callExternalOne(param1)
    second <- callNextExternal(first, param2, param3)
    result = getResultViaSecond(second)
  } yield result
}

val resultLayer = GrpcServiceLive.layer >>> AddItemServiceLive.layer

val result3 = addItemProgram3(100, 200, 300).provideLayer(resultLayer)

@main def hello() = {
  val value3Future = zio.Runtime.default.unsafeRunToFuture(result3)
  val value3 = Await.result(value3Future, Duration(5, TimeUnit.SECONDS))
  println(value3._1)
  println(value3._2)

  val value = Await.result(result, Duration(5, TimeUnit.SECONDS))
  println(value._1)
  println(value._2)

  val value2 = Await.result(result2, Duration(5, TimeUnit.SECONDS))
  println(value2._1)
  println(value2._2)
}

/**
 * (state, command) => ZIO[Has[CommandService], CommandError, (Result, List[Event])]
 * 
 * 
 * trait Command
 * 
 * trait ExecuteCommand
 * trait ReplyCommand
 * 
 * AddItem extends ExecuteCommand
 * AddItemReply extends ReplyCommand
 * 
 * commandExecuteHandler: (State, Command) => ZIO[Has[AddItemService], AddItemError, (AddItemResult, List[Event])]
 * 
 * ---
 * 
 * case AddItem(?, ?, ?, replyTo) =>
 *   val io = AddItemService.commandExecuteHandler(state, command).provideLayer(addItemLayer)
 *   val future = zio.Runtime.default.unsafeRunToFuture(io)
 * 
 *   context.pipeToSelf(future) { result =>
 *     match result {
 *       case Success(value) => AddItemReply(value._1, value._2, replyTo)
 *       case Failure(e) => ???
 *     }
 *   }
 * 
 *   Effect.stash()
 * 
 * ---
 * 
 * case AddItemReply(result, events, replyTo) =>
 *   Effect.persistAll(events).thenReply(_ => replyTo ! result)
 * 
 */