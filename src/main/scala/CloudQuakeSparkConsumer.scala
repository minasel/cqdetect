/* SimpleApp.scala */
import java.nio.ByteBuffer
import scala.util.Random
import org.apache.spark.Logging
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.Milliseconds
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.StreamingContext.toPairDStreamFunctions
import org.apache.spark.streaming.kinesis.KinesisUtils
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.services.kinesis.AmazonKinesisClient
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.InitialPositionInStream
import com.amazonaws.services.kinesis.model.PutRecordRequest
import org.apache.log4j.Logger
import org.apache.log4j.Level



object CloudQuakeSparkConsumer {
   def main(args: Array[String]) {

     /* Check that all required args were passed in. */
     if (args.length < 2) {
        System.err.println(
	   """
	      |Usage: KinesisWordCount <stream-name> <endpoint-url>
	      | <stream-name> is the name of the Kinesis stream
	      | <endpoint-url> is the endpoint of the Kinesis service
	      | (e.g. https://kinesis.us-east-1.amazonaws.com)
	   """.stripMargin)
	   System.exit(1) 
     }


     // =========================================
     // THIS IS SOME PRELIMINARY TESTING

     // This shows how to read files and do something with them
     val logFile = "file:///root/spark/README.md" // search in local path not hdfs
     val conf = new SparkConf().setAppName("Simple Application")
     val sc = new SparkContext(conf)
     val logData = sc.textFile(logFile, 2).cache()
     val numAs = logData.filter(line => line.contains("a")).count()
     val numBs = logData.filter(line => line.contains("b")).count()
     println("Lines with a: %s, Lines with b: %s".format(numAs, numBs))

     // =========================================




     // =========================================
     //     StreamingExamples.setStreamingLogLevels()

     /* Populate the appropriate variables from the given args */
     val Array(streamName, endpointUrl) = args
     println("Argument 1: %s, Argument 2: %s".format(streamName, endpointUrl))



     /* Determine the number of shards from the stream */
     val kinesisClient = new AmazonKinesisClient(new DefaultAWSCredentialsProviderChain().getCredentials())
     kinesisClient.setEndpoint(endpointUrl)
     val numShards = kinesisClient.describeStream(streamName).getStreamDescription().getShards()
      .size()

     /* In this example, we're going to create 1 Kinesis Worker/Receiver/DStream for each shard. */
     val numStreams = numShards

     /* Setup the and SparkConfig and StreamingContext */
     /* Spark Streaming batch interval */
     val batchInterval = Milliseconds(2000)
     val sparkConfig = new SparkConf().setAppName("KinesisWordCount")
     val ssc = new StreamingContext(sparkConfig, batchInterval)


     /* Kinesis checkpoint interval.  Same as batchInterval for this example. */
     val kinesisCheckpointInterval = batchInterval

     /* Create the same number of Kinesis DStreams/Receivers as Kinesis stream's shards */
     val kinesisStreams = (0 until numStreams).map { i =>
         KinesisUtils.createStream(ssc, streamName, endpointUrl, kinesisCheckpointInterval,
         InitialPositionInStream.LATEST, StorageLevel.MEMORY_AND_DISK_2)
     }


     /* Union all the streams */
     val unionStreams = ssc.union(kinesisStreams)


     /* Now our processing logic starts, for now I just count popular hashtags 
        another idea might be to count the most popular words. But will require
        analysing the json format. How can I open such a format.
     */
     

//   val kinesisClient = new AmazonKinesisClient()
//   val kinesisClient = new AmazonKinesisClient(new DefaultAWSCredentialsProviderChain().getCredentials())

     


  }
}