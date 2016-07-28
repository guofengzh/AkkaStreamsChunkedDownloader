package com.jasonmartens.s3downloader

import java.util.Calendar

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.util.ByteString
import com.amazonaws.HttpMethod
import com.amazonaws.auth.PropertiesFileCredentialsProvider
import com.amazonaws.services.s3.AmazonS3Client
import com.jasonmartens.s3downloader.ChunkPublisher.RequestChunk

import scala.collection.immutable.NumericRange
import scala.concurrent.ExecutionContextExecutor


/**
 * Created by Jason Martens <me@jasonmartens.com> on 6/12/15.
 *
 */

case class InitData(url: String, dataLength: Long)
object GetS3SignedURL {
  def apply(credentialsPath: String, bucketName: String, keyName: String): InitData = {
    val credentials = new PropertiesFileCredentialsProvider(credentialsPath)
    val awsClient = new AmazonS3Client(credentials)
    val expiration = Calendar.getInstance()
    expiration.add(Calendar.HOUR, 6)
    val s3Url = awsClient.generatePresignedUrl(bucketName, keyName, expiration.getTime, HttpMethod.GET)
    val s3Object = awsClient.getObject(bucketName, keyName)
    val size = s3Object.getObjectMetadata.getInstanceLength
    InitData(s3Url.toString, size)
  }
}

object GenerateFixedChunks {
  def apply(size: Long, chunkSize: Long): List[RequestChunk] = {
    val offsetList = NumericRange[Long](0, size, chunkSize)
    val sizeList = offsetList.init.map(_ => chunkSize) :+ size % chunkSize
    val chunkNumbers = Range(1, offsetList.end.toInt)
    val zipped = offsetList zip sizeList zip chunkNumbers
    zipped.map {case ((o, s), n) => RequestChunk(n, o, s)}.toList
  }
}


object S3Downloader extends App {
  val credentialsPath = args(0)
  val bucketName = args(1)
  val keyName = args(2)

  implicit val system = ActorSystem(s"s3download-system")
  implicit val materializer = ActorMaterializer()
  implicit def executor: ExecutionContextExecutor = system.dispatcher

  val initData = GetS3SignedURL(credentialsPath, bucketName, keyName)
  val chunkList = GenerateFixedChunks(initData.dataLength, 1024 * 1024 * 1)
  println(s"chunkList: $chunkList")

  val source =
    Source.actorPublisher[ByteString](ChunkPublisher.props(initData.url, chunkList))
  val res = source.map(elem => elem.length).runWith(Sink.fold[Int, Int](0)(_ + _))
  res.onComplete({result => println(s"Got ${result.get} bytes"); system.shutdown()})

}
