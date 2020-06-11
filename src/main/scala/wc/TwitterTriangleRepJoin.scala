package wc

import java.io.PrintWriter

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.log4j.LogManager
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.expressions.scalalang._
import util.control.Breaks._

object TwitterTriangleRepJoin {

  def main(args: Array[String]) {
    val logger: org.apache.log4j.Logger = LogManager.getRootLogger
    if (args.length != 4) {
      logger.error("Usage:\nwc.TwitterTriangleRepJoin <input dir> <output dir> <max-filter-count> <type>")
      System.exit(1)
    }
    val conf = new SparkConf().setAppName("Twitter Follower Count")
    val sc = new SparkContext(conf)

    val sparkSession = SparkSession.builder.
      master("local-rep")
      .appName("spark session example")
      .getOrCreate()

    val maximum = args(2).toInt
    //MaxFilter(sc,args(0),temp,args(2))
    val dataType = args(3)
    if(dataType.equals("RDD")){
      RDDRep(conf,sc,args(0),args(1),maximum)
    }else{
      DSETRep(sparkSession,sc,args(0),args(1),maximum)
      //DSETSo(sparkSession,sc,args(0),args(1))
    }
  }

  def MaxFilter(sc: SparkContext, input : String,output : String, max :String)={
    val textFile = sc.textFile(input)
    val maximum = max.toInt
    val counts = textFile.filter(word => {
      val users = word.split(",")
      val fromUser = users(0).toInt
      val toUser = users(1).toInt
      (fromUser < maximum && toUser < maximum)
    })
    counts.saveAsTextFile(output)
  }


  def RDDRep(conf: SparkConf, sc: SparkContext, input : String, output: String,maximum : Int): Any ={

    println("RDD Reeplicated Join-------------------------------------")
    val textFile = sc.textFile(input)

    val maxfilter = textFile.filter(edges =>{
      val users = edges.split(",");
      val fromUser = users(0).toInt
      val toUser = users(1).toInt
      (fromUser < maximum && toUser < maximum)
    })


    val fromUsers = maxfilter.map(edges =>{
      val users = edges.split(",");
      (users(0),users(1))
    }).groupBy(_._1).mapValues(_.map(_._2).toList)

    val smallRDDLocal = fromUsers.collectAsMap()
    val broadcastedMap = sc.broadcast(smallRDDLocal)

    val toUsers = maxfilter.map(edges =>{
      val users = edges.split(",");
      (users(1),users(0))
    })

    val addToCounts = (n: Int, v: Int) => n + v
    val sumPartitionCounts = (p1: Int, p2: Int) => p1 + p2
    val twoPath = toUsers.map(word =>{
      var count = 0
        if(broadcastedMap.value.get(word._1) != None){
          val path2 = broadcastedMap.value.get(word._1).get
          for(a <- path2){
            if(broadcastedMap.value.get(a) != None){
              val path3 = broadcastedMap.value.get(a).get
              for(b <- path3) {
                if(b == word._2) {
                  println("triangle123")
                  count += 1
                }
              }
            }
          }
        }
      ("Triangle",count)
    }).filter(a => {
      (a._2 > 0)
    }).aggregateByKey(0)(addToCounts, sumPartitionCounts)

    var totalTriangles = twoPath.map(count => {
      ("RDD Replicated Join",count._2.toInt/3)
    })

    totalTriangles.coalesce(1).saveAsTextFile(output)
  }


  def DSETRep(sparkSession: SparkSession, sc: SparkContext, input : String, output: String,maximum: Int)={
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

    val broadcastedFromUser = sc.broadcast(fromUsers)
    val broadcastedToUser = sc.broadcast(toUsers)

    // finding two paths using broadcasted RDD
    var fromDF = broadcastedFromUser.value.toDF("twoPathNode","source")
    var toDF = broadcastedToUser.value.toDF("twoPathNode","destination")
    var twoPath = fromDF.join(toDF,"twoPathNode")

    // finding a triangle which completes from two paths found
    var fromLastPath = broadcastedFromUser.value.toDF("source","destination")
    var threePath = twoPath.toDF().join(fromLastPath,Seq("source","destination"))


    var finalOutput = threePath.rdd.map(count =>{
      ("DF Replicated Join",1)
    }).reduceByKey(_ + _).map(count => { (count._1,count._2/3) })

//    var totalTriangles = threePath.map(count => {
//      ("RDD Replicated Join",count._2.toInt/3)
//    })
    finalOutput.coalesce(1).saveAsTextFile(output)

//    threePath.coalesce(1).write.csv(output)
//
//    val totalTriangles = threePath.count()/3
//    new PrintWriter(output + "/count.txt") { write("DF Replicated Join-"+totalTriangles); close }

  }
}