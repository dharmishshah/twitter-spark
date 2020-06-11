package wc

import java.io.PrintWriter

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.log4j.LogManager
import org.apache.log4j.Level
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.expressions.scalalang._

object TwitterTriangleReduceJoin {

  def main(args: Array[String]) {
    val logger: org.apache.log4j.Logger = LogManager.getRootLogger
    if (args.length != 4) {
      logger.error("Usage:\nwc.WordCountMain <input dir> <output dir> <maxfilter> <type>")
      System.exit(1)
    }
    val conf = new SparkConf().setAppName("Twitter Follower Count")
    val sc = new SparkContext(conf)

    val sparkSession = SparkSession.builder.
      master("local")
      .appName("spark session example")
      .getOrCreate()

    val maximum = args(2).toInt
   // MaxFilter(sc,args(0),temp,args(2))
    val dataType = args(3)
    if(dataType.equals("RDD")){
      RDDReduce(conf,sc,args(0),args(1),maximum)
    }else{
      DSETReduce(sparkSession,args(0),args(1),maximum)
    }
  }

  // This function is used to filter twitter followe following edges based on MAX_VALUES.
  // It worked successfully on local, but failed in AWS.
  // So, this was handled internally in joins
  def MaxFilter(sc: SparkContext, input : String,output : String, max :String)={
    val textFile = sc.textFile(input)
    val maximum = max.toInt
    val counts = textFile.filter(word => {
      val users = word.split(",")
      val fromUser = users(0).toInt
      val toUser = users(1).toInt
      (fromUser < maximum && toUser < maximum)
    })
    counts.coalesce(1).saveAsTextFile(output)

  }

  def RDDReduce(conf: SparkConf, sc: SparkContext, input : String, output: String, maximum : Int)={
    println("RDD Reduce Join-------------------------------------")
    val textFile = sc.textFile(input)

    val maxfilter = textFile.filter(edges =>{
      val users = edges.split(",");
      val fromUser = users(0).toInt
      val toUser = users(1).toInt
      (fromUser < maximum && toUser < maximum)
    })

    println("----------------------filtering done------------------")

    val fromUsers = maxfilter.map(edges =>{
      val users = edges.split(",");
      (users(0),users(1))
    })

    val toUsers = maxfilter.map(edges =>{
      val users = edges.split(",");
      (users(1),users(0))
    })

    val twoPath = fromUsers.join(toUsers)

    val processedtwoPath = twoPath.map(twoPath =>{
       val secondPath = twoPath._2.toString().replace("(","").replace(")","")
         .split(",")
      ((secondPath(0),secondPath(1)),1)
    })

    val fromUsersAsKey = maxfilter.map(edges =>{
      val users = edges.split(",");
      ((users(0),users(1)),1)
    })

    val threePath = processedtwoPath.join(fromUsersAsKey)

    var finalOutput = threePath.map(count =>{
      ("RDD Reduce Join",1)
    }).reduceByKey(_ + _).map(count => { (count._1,count._2/3) })


    finalOutput.coalesce(1).saveAsTextFile(output)
//    // removing duplicates
//    val totalTriangles = threePath.count()/3
//    new PrintWriter(output + "/count.txt") { write("RDD Reduce Join - "+totalTriangles); close }

  }


  def DSETReduce(sparkSession: SparkSession, input : String, output: String,maximum :Int)={
    import sparkSession.implicits._
    val data = sparkSession.read.text(input).as[String]

    val maxfilter = data.filter(edges =>{
      val users = edges.split(",");
      val fromUser = users(0).toInt
      val toUser = users(1).toInt
      (fromUser < maximum && toUser < maximum)
    })

    val fromUsers = maxfilter.map(edges =>{
      val users = edges.split(",");
      (users(0),users(1))
    })

    val toUsers = maxfilter.map(edges =>{
      val users = edges.split(",");
      (users(1),users(0))
    })

    // finding two paths
    var fromDF = fromUsers.toDF("twoPathNode","source")
    var toDF = toUsers.toDF("twoPathNode","destination")
    var twoPath = fromDF.join(toDF,"twoPathNode")

    // finding a triangle which completes from two paths found
    var fromLastPath = fromUsers.toDF("source","destination")
    var threePath = twoPath.toDF().join(fromLastPath,Seq("source","destination"))
    //threePath.coalesce(1).write.csv(output)

    var finalOutput = threePath.rdd.map(count =>{
      ("DF Reduce Join",1)
    }).reduceByKey(_ + _).map(count => { (count._1,count._2/3) })

    finalOutput.coalesce(1).saveAsTextFile(output)

//    val totalTriangles = threePath.count()/3
//    new PrintWriter(output + "/count.txt") { write("DF Reduce Join - "+totalTriangles); close }
  }


}