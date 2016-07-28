package com.jasonmartens.s3downloader

import akka.actor.Props
import akka.actor.Status.Success
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{ByteRange, Range}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, ResponseEntity, StatusCodes}
import akka.stream.ActorMaterializer
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import com.jasonmartens.s3downloader.ChunkPublisher._

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.Try

/**
 * Created by Jason Martens <me@jasonmartens.com> on 6/12/15.
 *
 */
object ChunkPublisher {
  case class DownloadedChunk(data: ByteString)
  case class RequestChunk(number: Int, offset: Long, size: Long)
  case class ChunkDownloaded(number: Int, response: Try[HttpResponse])
  case class ChunkData(number: Int, data: Try[ByteString])
  case class DownloadFailed(number: Int)
  case class DownloadTimeout(number: Int)
  def props(url: String, chunkList: List[RequestChunk]): Props =
    Props(new ChunkPublisher(url, chunkList))
}
class ChunkPublisher(url: String, chunkList: List[RequestChunk]) extends ActorPublisher[ByteString] {
  implicit val system = context.system
  implicit val executionContext = context.dispatcher
  implicit val materializer = ActorMaterializer()

  sealed trait DownloadState
  case object Ready extends DownloadState
  case object Requested extends DownloadState
  case object Completed extends DownloadState

  var nextChunkToEmit = 1
  var chunkMap: Map[Int, RequestChunk] = chunkList.map(c => c.number -> c).toMap
  var chunkState: Map[Int, DownloadState] = chunkList.map(c => c.number -> Ready).toMap
  val completedChunks: mutable.Map[Int, ByteString] =
    mutable.Map[Int, ByteString]()
  var inFlightDemand = 0
  val downloadTimeout: FiniteDuration = 10 seconds

  def netDemand = totalDemand - inFlightDemand

  def downloadChunk(chunk: RequestChunk): Unit = {
    //implicit val materializer = ActorMaterializer.create(system);
    val request = HttpRequest(
      uri = url.toString,
      // Range is inclusive, so -1
      headers = List(Range(ByteRange(chunk.offset, (chunk.offset + chunk.size) - 1))))
      val response: Future[HttpResponse] = Http().singleRequest(request)

      def dealWith(entity:ResponseEntity): Unit = {
        val source = entity.dataBytes
        val fut = source.grouped(10000000).runWith(Sink.head)
        fut.onComplete(data => self ! ChunkData(chunk.number, data.map(d => d.fold(ByteString())(_ ++ _))))
      }

    response.map({
      case HttpResponse(StatusCodes.OK, headers, entity, _) =>
        //onNext(ByteString.fromString("Hahaha"))
        //requestChunks() ;
        dealWith(entity) ;
        //self ! ChunkData(chunk.number, Try(ByteString.fromString("Babao")))
      case HttpResponse(StatusCodes.PartialContent, headers, entity, _) =>
        //onNext(ByteString.fromString("Babao"))
        //requestChunks() ;
        dealWith(entity) ;
        //self ! ChunkData(chunk.number, Try(ByteString.fromString("Babao")))
      case HttpResponse(code, _, _, _) =>
        println("Request failed, response code: " + code)
        self ! DownloadFailed(chunk.number)
    })
    println(s"Downloading chunk ${chunk.number}")
    //response.onComplete(data => self ! ChunkDownloaded(chunk.number, data))
    response.onFailure {case ex: Exception => println(ex); self ! DownloadFailed(chunk.number)}
    //context.system.scheduler.scheduleOnce(downloadTimeout, self, DownloadTimeout(chunk.number))
  }

  def remainingChunks: List[Int] =
    chunkState.filter(_._2 == Ready).keys.toList.sorted

  def allChunksCompleted: Boolean = chunkState.forall(elem => elem._2 == Completed)

  def requestChunks(): Unit = {
    try {
      println(s"requestChunks")
      var chunksLeft = remainingChunks
      if (netDemand > 0 && chunksLeft.nonEmpty) {
        println(s"requesting chunks: totalDemand: $totalDemand, inFlightDemand: $inFlightDemand")
        val num = chunksLeft.head
        chunkState = chunkState.updated(num, Requested)
        downloadChunk(chunkMap(num))
        inFlightDemand += 1
        chunksLeft = chunksLeft.tail
      }
      else {
        println(s"No demand - totalDemand: $totalDemand, inFlightDemand: $inFlightDemand")
      }
    } catch {
      case ex: Exception => println(ex)
    }
  }

  def emitChunks(): Unit = {
    println(s"emitChunks - Active: $isActive")

    // 似乎应该是：
    // while (isActice && completedCunks.contas(nextChunkToEmit)， 这样，可以
    // 确保在之前的失败的chunk成功之后，其后的chunk能够也被emit掉
    // 问题就变成：
    //    根据totalDemand来确定:
    //       1,应该有多少http request
    //       2.在有totaldemand和nextChunkToEmit的情况下，emit，每emit一个，都要发个http request的请求
    //       http请求不能多于totaldemand，但又不能太多。
    if (isActive && completedChunks.contains(nextChunkToEmit)) {
        println(s"emitting chunk $nextChunkToEmit")
        onNext(completedChunks(nextChunkToEmit))
        completedChunks.remove(nextChunkToEmit)
        nextChunkToEmit += 1
        inFlightDemand -= 1
        requestChunks()
    }
    else {
      println(s"Chunk $nextChunkToEmit failed")
      self ! DownloadFailed(nextChunkToEmit)
    }
    if (allChunksCompleted) {
      println("All chunks completed...")
      onCompleteThenStop()
    };
  }

  def chunkData(number: Int, data: Try[ByteString]): Unit = {
    if (data.isSuccess) {
      println(s"chunkData $number succeeded")
      completedChunks.update(number, data.get)
      chunkState = chunkState.updated(number, Completed)
      emitChunks()
    }
    else {
      println("chunkData failed")
      self ! DownloadFailed(number)
    }
  }

  override def receive: Receive = {
    case Request(_) => println("Request"); requestChunks()
    case Cancel => println("Cancel"); context.stop(self)
    case Success => println("Success")
    //case ChunkDownloaded(n, r) => println("ChunkDownloaded"); chunkComplete(n, r)
    case ChunkData(n, d) => println("ChunkData"); chunkData(n, d)
    case DownloadFailed(n) => println("DownloadFailed"); downloadChunk(chunkMap(n))
    case DownloadTimeout(n) => println("DownloadTimeout"); downloadChunk(chunkMap(n))
    case x => println(s"Got unknown message $x")
  }
  override def postStop(): Unit = {
    super.postStop()

    println( "Stopped") ;
  }
}
