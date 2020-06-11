package wc

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.log4j.LogManager
import org.apache.spark.sql.SparkSession

object TwitterCountMain {
  
  def main(args: Array[String]) {
    val logger: org.apache.log4j.Logger = LogManager.getRootLogger
    if (args.length != 3) {
      logger.error("Usage:\nwc.WordCountMain <input dir> <output dir> <type>")
      System.exit(1)
    }
    val conf = new SparkConf().setAppName("Twitter Follower Count")
    val sc = new SparkContext(conf)

    val sparkSession = SparkSession.builder.
      master("local")
      .appName("spark session example")
      .getOrCreate()

    val program = args(2)
    if(program.equals("RDDG"))
      RDDG(conf,sc,args(0),args(1))
    else if(program.equals("RDDR"))
      RDDR(conf,sc,args(0),args(1))
    else if(program.equals("RDDF"))
      RDDF(conf,sc,args(0),args(1))
    else if(program.equals("RDDA"))
      RDDA(conf,sc,args(0),args(1))
    else if(program.equals("DSET"))
      DSET(sparkSession,args(0),args(1))
    else
      None


  }

  def RDDG(conf: SparkConf, sc: SparkContext, input : String, output: String)={
    val textFile = sc.textFile(input)
    val counts = textFile.flatMap(line => line.split(" "))
      .map(word => {
        val users = word.split(",")
        (users(1), 1)
      })
      .groupByKey().map(word => (word._1, word._2.sum))
    println(counts.toDebugString)
    counts.saveAsTextFile(output)
  }

  def RDDR(conf: SparkConf, sc: SparkContext, input : String, output: String)={
    val textFile = sc.textFile(input)
    val counts = textFile.flatMap(line => line.split(" "))
      .map(word => {
        val users = word.split(",")
        (users(1), 1)
      })
      .reduceByKey(_ + _)
    println(counts.toDebugString)
    counts.saveAsTextFile(output)
  }

  def RDDF(conf: SparkConf, sc: SparkContext, input : String, output: String)={
    val textFile = sc.textFile(input)
    val counts = textFile.flatMap(line => line.split(" "))
      .map(word => {
        val users = word.split(",")
        (users(1), 1)
      })
      .foldByKey(10)(_ + _)
    println(counts.toDebugString)
    counts.saveAsTextFile(output)
  }

  def RDDA(conf: SparkConf, sc: SparkContext, input : String, output: String)={
    val textFile = sc.textFile(input)

    def seqOp = (accumulator: Int, element: (Int)) =>
      accumulator + element

    //Combiner Operation : Finding Maximum Marks out Partition-Wise Accumulators
    def combOp = (accumulator1: Int, accumulator2: Int) =>
       accumulator1 + accumulator2


    val counts = textFile.flatMap(line => line.split(" "))
      .map(word => {
        val users = word.split(",")
        (users(1), 1)
      })
      .aggregateByKey(0)(seqOp, combOp)
    println(counts.toDebugString)
    counts.saveAsTextFile(output)
  }

  def DSET(sparkSession: SparkSession, input : String, output: String)={
    import sparkSession.implicits._
    val data = sparkSession.read.text(input).as[String]
    val words = data.flatMap(value => value.split(" "))
    val mapWords = words.map(word => {
      val users = word.split(",")
      (users(1), 1)
    })
    val groupedWords = mapWords.groupBy($"_1").sum()
    //val groupedWords = mapWords.groupByKey(_._1).count().withColumnRenamed("count(1)","count")
//    val groupedWords = mapWords.groupByKey(_._1).agg(typed.sumLong(_._2)).
//      withColumnRenamed("TypedSumLong(scala.Tuple2)","Count")
    groupedWords.show()
    println(groupedWords.explain())
    groupedWords.coalesce(1).write.csv(output)
  }


}