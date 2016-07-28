package com.jasonmartens.downloader;

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.util.ByteString
import akka.stream.scaladsl.Source
import com.jasonmartens.s3downloader.{ChunkPublisher, GenerateFixedChunks}

import scala.concurrent.{ExecutionContextExecutor, Future, Promise}
import scala.util.Failure

object DownloaderMain {
    implicit val system = ActorSystem.create() ;
    implicit val materializer = ActorMaterializer.create(system);
    implicit def executor: ExecutionContextExecutor = system.dispatcher

    def main(args : Array[String] ) {
        val url = "<URL>" ;
        val chunkList = GenerateFixedChunks(1024 * 50 + 120, 1024) ;

      println(s"chunkList: $chunkList")

      /*
      val source =
        Source.actorPublisher[ByteString](ChunkPublisher.props(url, chunkList))
      val res = source.map(elem => elem.length).runWith(Sink.fold[Int, Int](0)(_ + _))
      res.onComplete({result => println(s"Got ${result.get} bytes"); system.shutdown()})
      */

        val source =
            Source.actorPublisher[ByteString](ChunkPublisher.props(url, chunkList))

      val res =source.runWith(Sink.foreach(b=>{
        println("-----------");
        println(b.utf8String);
      }))
      res.onComplete({result => println("Completed"); system.terminate()})
      res.onSuccess{
        case s => println("Success")
      }
      res.onFailure {
        case t => println("Failure: " + t.getMessage)
      }

     //Thread.sleep(60 * 1000);

    }
}
