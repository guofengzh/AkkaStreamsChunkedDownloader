# AkkaStreamsChunkedDownloader
Attempt to make a large file downloader using Akka Streams

This is a completely experimental project. 

The goal is to make an Akka Streams Source that can download arbitrarily large files 
from S3 (or another HTTP source) by splitting the file into chunks, and emitting each
chunk. An important feature (and why I am not using a Source[Source[ByteString, Unit], Unit])
is that I want to be able to retry any given chunk for an arbitrary amount of time, 
should the initial attempt to download the chunk fail without causing the stream to fail.

