/* SimpleApp.scala */
import java.nio.ByteBuffer
import scala.util.Random
import org.apache.spark.Logging
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.{Milliseconds,Seconds}
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
     /* Populate the appropriate variables from the given args */
     val Array(streamName, endpointUrl) = args
     println("Stream to connect: %s, Amazon backend: %s".format(streamName, endpointUrl))

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

     /* Set checkpoint directory */
     ssc.checkpoint("/root/check/")

     /* Kinesis checkpoint interval.  Same as batchInterval for this example. */
     val kinesisCheckpointInterval = batchInterval

     /* Create the same number of Kinesis DStreams/Receivers as Kinesis stream's shards */
     val kinesisStreams = (0 until numStreams).map { i =>
         KinesisUtils.createStream(ssc, streamName, endpointUrl, kinesisCheckpointInterval,
         InitialPositionInStream.LATEST, StorageLevel.MEMORY_AND_DISK_2)
     }


     /* list of words to search for */
     val keyWords = List("this")
//     val keyWords = List("earthquake", "Earthquake", "Erdbeben", "Quake", "shaking")


     /* Union all the streams */
     val unionStreams = ssc.union(kinesisStreams)

     // val windowed_unionStreams = unionStreams.window(new Duration(60000), new Duration(5000))

     // This just outputs everything contained in the tweet
   val words = unionStreams.flatMap(byteArray => new String(byteArray).split(" "))
   val lines = unionStreams.flatMap(byteArray => new String(byteArray).split("\n"))     
     // words.print()

     // This
     val hashTags = unionStreams.flatMap(byteArray => new String(byteArray).split(" ").filter(_.startsWith("#")))
     // hashTags.print()


     /* Here I could insert looking for words like EQ,earthquake,... etc */
//     val containsEbola = unionStreams.flatMap(byteArray => new String(byteArray).split("\n"))
//     val containsthis = unionStreams.flatMap(byteArray => new String(byteArray).split("\n").filter(_.exists(keyWords contains _)))

     val containsthis = unionStreams.flatMap(byteArray => new String(byteArray).split(" ").filter(_.exists(s => s == "this")))


     


     val alltweets = unionStreams.flatMap(byteArray => new String(byteArray).split("\n")).countByWindow(Seconds(2),Seconds(2))

     containsthis.print()
//     containsthis.countByWindow(Seconds(60),Seconds(2)).print()
     alltweets.print()

//     print(countEbola)
     
     // Most popular hashtags in the last 60 seconds
     val topCounts60 = hashTags.map((_, 1)).reduceByKeyAndWindow(_ + _, Seconds(60))
     	              .map{case (topic, count) => (count, topic)}
              	      .transform(_.sortByKey(false))

     // Most popular hashtags in the last 10 seconds
     val topCounts10 = hashTags.map((_, 1)).reduceByKeyAndWindow(_ + _, Seconds(10))
     	              .map{case (topic, count) => (count, topic)}
              	      .transform(_.sortByKey(false))


     // Most popular words in the last 60 seconds
     val topWordCounts60 = words.map((_, 1)).reduceByKeyAndWindow(_ + _, Seconds(60))
     	              .map{case (topic, count) => (count, topic)}
              	      .transform(_.sortByKey(false))


     // Most popular words in the last 60 seconds
//     val topEbolaCounts60 = containsEbola.map((_, 1)).reduceByKeyAndWindow(_ + _, Seconds(60))
//     	              .map{case (topic, count) => (count, topic)}







     /*

      // Print popular hashtags
      topCounts60.foreachRDD(rdd => {
      val topList = rdd.take(10)
      println("\nPopular topics in last 60 seconds (%s total):".format(rdd.count()))
      topList.foreach{case (count, tag) => println("%s (%s tweets)".format(tag, count))}
      })


      // Print tweets with ebola
      topEbolaCounts60.foreachRDD(rdd => {
      val topList = rdd.take(10)
      println("\nPopular topics in last 60 seconds (%s total):".format(rdd.count()))
      topList.foreach{case (count, tag) => println("%s (%s tweets)".format(tag, count))}
      })
     



      // Print popular hashtags
      topWordCounts60.foreachRDD(rdd => {
      val topList = rdd.take(10)
      println("\nPopular topics in last 60 seconds (%s total):".format(rdd.count()))
      topList.foreach{case (count, tag) => println("%s (%s tweets)".format(tag, count))}
      })


      topCounts10.foreachRDD(rdd => {
      val topList = rdd.take(10)
      println("\nPopular topics in last 10 seconds (%s total):".format(rdd.count()))
      topList.foreach{case (count, tag) => println("%s (%s tweets)".format(tag, count))}
      })


      */


     /* Now our processing logic starts, for now I just count popular hashtags 
        another idea might be to count the most popular words. But will require
        analysing the json format. How can I open such a format.
     */
     

    /* Start the streaming context and await termination */
    ssc.start()
    ssc.awaitTermination()



  }
}