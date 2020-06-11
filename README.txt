Spark Scala Twitter Triangles
Homework 3 Submission
Spring 2020

Code author
-----------
Dharmish Shah

Installation
------------
These components are installed:
- JDK 1.8
- Scala 2.11.12
- Hadoop 2.9.1
- Spark 2.3.1 (without bundled Hadoop)
- Maven
- AWS CLI (for EMR execution)

Environment
-----------
1) Example ~/.bash_aliases:
export JAVA_HOME=/usr/lib/jvm/java-8-oracle
export HADOOP_HOME=/home/joe/tools/hadoop/hadoop-2.9.1
export SCALA_HOME=/home/joe/tools/scala/scala-2.11.12
export SPARK_HOME=/home/joe/tools/spark/spark-2.3.1-bin-without-hadoop
export YARN_CONF_DIR=$HADOOP_HOME/etc/hadoop
export PATH=$PATH:$HADOOP_HOME/bin:$HADOOP_HOME/sbin:$SCALA_HOME/bin:$SPARK_HOME/bin
export SPARK_DIST_CLASSPATH=$(hadoop classpath)

2) Explicitly set JAVA_HOME in $HADOOP_HOME/etc/hadoop/hadoop-env.sh:
export JAVA_HOME=/usr/lib/jvm/java-8-oracle

Submission
------------------------------------
1) Unzip project file.
2) Navigate to directory where project files are unzipped.
3) Go to output folder.
4) The program was ran on AWS, for four joins, namely ReduceJoin-RDD, ReduceJoin-DF, ReplicatedJoin-RDD
   and ReplicatedJoin-DF, on 6 machine instance and 7 machine instance respectively with max user value = 10000.
5) controller and output file can be found for respective machine instance and joins.
    NOTE - AWS runs produced syserr files, but it showed final status for each run as SUCCEEDED
6) Implementation of Twitter Follower count for all 5 types is in TwitterCountMain.java
   Implementation of Replicated Join using RDD and DataFrame is in TwitterTriangleRepJoin.java
   Implementation of Reduce Join using RDD and DataFrame is in TwitterTriangleReduceJoin.java

Execution
---------
All of the build & execution commands are organized in the Makefile.
1) Unzip project file.
2) Open command prompt.
3) Navigate to directory where project files unzipped.
4) Edit the Makefile to customize the environment at the top.
	Sufficient for standalone: hadoop.root, jar.name, local.input
	Other defaults acceptable for running standalone.
5) Standalone Hadoop:
	make switch-standalone		-- set standalone Hadoop environment (execute once)
	make local                  -- to run Twitter Follower count using programType = $(value) where
	                                value IN (RDDG, RDDR, RDDF, RDDA and DSET)
	make local-reduce-rdd       -- run reduce join using RDD on local
	make local-reduce-df        -- run reduce join using DataFrames on local
	make local-rep-rdd          -- run replicated join using RDD on local
	make local-rep-df           -- run replicated join using DataFrames on local
6) Pseudo-Distributed Hadoop: (https://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-common/SingleCluster.html#Pseudo-Distributed_Operation)
	make switch-pseudo			-- set pseudo-clustered Hadoop environment (execute once)
	make pseudo					-- first execution
	make pseudoq				-- later executions since namenode and datanode already running 
7) AWS EMR Hadoop: (you must configure the emr.* config parameters at top of Makefile)
	make upload-input-aws		-- only before first execution
	make aws					-- run twitter follower count on aws (aws.amazon.com)
	make aws-reduce-rdd       -- run reduce join using RDD on aws
    make aws-reduce-df        -- run reduce join using DataFrames on aws
    make aws-rep-rdd          -- run replicated join using RDD on aws
    make aws-rep-df           -- run replicated join using DataFrames on aws
	download-output-aws			-- after successful execution & termination, download output folder from aws
