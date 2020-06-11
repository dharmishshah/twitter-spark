# Makefile for Spark WordCount project.

# Customize these paths for your environment.
# -----------------------------------------------------------
spark.root=/home/dharmish/work/software/spark-2.3.1-bin-without-hadoop
hadoop.root=/home/dharmish/work/software/hadoop-2.9.1
app.name=Twitter Follower Count
jar.name=spark-twitter-count.jar
maven.jar.name=spark-twitter-count-1.0.jar
job.name=wc.TwitterCountMain
jobRep.name=wc.TwitterTriangleRepJoin
jobReduce.name=wc.TwitterTriangleReduceJoin
jobTypeRDD = RDD
jobTypeDF = DF
programType = RDDG
max-filter=10000
local.master=local[4]
local.input=input
local.output=output
# Pseudo-Cluster Execution
hdfs.user.name=dharmish
hdfs.input=input
hdfs.output=output
# AWS EMR Execution
aws.emr.release=emr-5.17.0
aws.bucket.name=spark-twitter-triangle
aws.input=input
aws.output=output
aws.log.dir=log
aws.num.nodes=6
aws.instance.type=m5.xlarge
# -----------------------------------------------------------

# Compiles code and builds jar (with dependencies).
jar:
	mvn clean package
	cp target/${maven.jar.name} ${jar.name}

# Removes local output directory.
clean-local-output:
	rm -rf ${local.output}* temp

# Runs standalone
local: jar clean-local-output
	spark-submit --class ${job.name} --master ${local.master} --name "${app.name}" ${jar.name} ${local.input} ${local.output} ${programType}

# Runs standalone for replicated join using RDD
local-rep-rdd: jar clean-local-output
	spark-submit --class ${jobRep.name} --master local[4] --name "${app.name}" ${jar.name} ${local.input} ${local.output} ${max-filter} ${jobTypeRDD}

# Runs standalone for replicated join using Dataframe
local-rep-df: jar clean-local-output
	spark-submit --class ${jobRep.name} --master local[4] --name "${app.name}" ${jar.name} ${local.input} ${local.output} ${max-filter} ${jobTypeDF}

# Runs standalone for reduce join using RDD
local-reduce-rdd: jar clean-local-output
	spark-submit --class ${jobReduce.name} --master ${local.master} --name "${app.name}" ${jar.name} ${local.input} ${local.output} ${max-filter} ${jobTypeRDD}

# Runs standalone for reduce join using dataframe
local-reduce-df: jar clean-local-output
	spark-submit --class ${jobReduce.name} --master ${local.master} --name "${app.name}" ${jar.name} ${local.input} ${local.output} ${max-filter} ${jobTypeDF}

# Start HDFS
start-hdfs:
	${hadoop.root}/sbin/start-dfs.sh

# Stop HDFS
stop-hdfs: 
	${hadoop.root}/sbin/stop-dfs.sh
	
# Start YARN
start-yarn: stop-yarn
	${hadoop.root}/sbin/start-yarn.sh

# Stop YARN
stop-yarn:
	${hadoop.root}/sbin/stop-yarn.sh

# Reformats & initializes HDFS.
format-hdfs: stop-hdfs
	rm -rf /tmp/hadoop*
	${hadoop.root}/bin/hdfs namenode -format

# Initializes user & input directories of HDFS.	
init-hdfs: start-hdfs
	${hadoop.root}/bin/hdfs dfs -rm -r -f /user
	${hadoop.root}/bin/hdfs dfs -mkdir /user
	${hadoop.root}/bin/hdfs dfs -mkdir /user/${hdfs.user.name}
	${hadoop.root}/bin/hdfs dfs -mkdir /user/${hdfs.user.name}/${hdfs.input}

# Load data to HDFS
upload-input-hdfs: start-hdfs
	${hadoop.root}/bin/hdfs dfs -put ${local.input}/* /user/${hdfs.user.name}/${hdfs.input}

# Removes hdfs output directory.
clean-hdfs-output:
	${hadoop.root}/bin/hdfs dfs -rm -r -f ${hdfs.output}*

# Download output from HDFS to local.
download-output-hdfs:
	mkdir ${local.output}
	${hadoop.root}/bin/hdfs dfs -get ${hdfs.output}/* ${local.output}

# Runs pseudo-clustered (ALL). ONLY RUN THIS ONCE, THEN USE: make pseudoq
# Make sure Hadoop  is set up (in /etc/hadoop files) for pseudo-clustered operation (not standalone).
# https://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-common/SingleCluster.html#Pseudo-Distributed_Operation
pseudo: jar stop-yarn format-hdfs init-hdfs upload-input-hdfs start-yarn clean-local-output 
	spark-submit --class ${job.name} --master yarn --deploy-mode cluster ${jar.name} ${local.input} ${local.output}
	make download-output-hdfs

# Runs pseudo-clustered (quickie).
pseudoq: jar clean-local-output clean-hdfs-output 
	spark-submit --class ${job.name} --master yarn --deploy-mode cluster ${jar.name} ${local.input} ${local.output}
	make download-output-hdfs

# Create S3 bucket.
make-bucket:
	aws s3 mb s3://${aws.bucket.name}

# Upload data to S3 input dir.
upload-input-aws: make-bucket
	aws s3 sync ${local.input} s3://${aws.bucket.name}/${aws.input}
	
# Delete S3 output dir.
delete-output-aws:
	aws s3 rm s3://${aws.bucket.name}/ --recursive --exclude "*" --include "${aws.output}*"

# Upload application to S3 bucket.
upload-app-aws:
	aws s3 cp ${jar.name} s3://${aws.bucket.name}

# Main EMR launch.
aws: jar upload-app-aws delete-output-aws
	aws emr create-cluster \
		--name "WordCount Spark Cluster" \
		--release-label ${aws.emr.release} \
		--instance-groups '[{"InstanceCount":${aws.num.nodes},"InstanceGroupType":"CORE","InstanceType":"${aws.instance.type}"},{"InstanceCount":1,"InstanceGroupType":"MASTER","InstanceType":"${aws.instance.type}"}]' \
	    --applications Name=Hadoop Name=Spark \
		--steps Type=CUSTOM_JAR,Name="${app.name}",Jar="command-runner.jar",ActionOnFailure=TERMINATE_CLUSTER,Args=["spark-submit","--deploy-mode","cluster","--class","${job.name}","s3://${aws.bucket.name}/${jar.name}","s3://${aws.bucket.name}/${aws.input}","s3://${aws.bucket.name}/${aws.output}"] \
		--log-uri s3://${aws.bucket.name}/${aws.log.dir} \
		--use-default-roles \
		--enable-debugging \
		--auto-terminate

# Main EMR launch using Replicated Join on RDD
aws-rep-rdd: jar upload-app-aws delete-output-aws
	aws emr create-cluster \
		--name "Triangle Spark Cluster Replicated Join RDD, MAX=${max-filter},${aws.instance.type},NODE=${aws.num.nodes}"\
		--release-label ${aws.emr.release} \
		--instance-groups '[{"InstanceCount":${aws.num.nodes},"InstanceGroupType":"CORE","InstanceType":"${aws.instance.type}"},{"InstanceCount":1,"InstanceGroupType":"MASTER","InstanceType":"${aws.instance.type}"}]' \
	    --applications Name=Hadoop Name=Spark \
		--steps Type=CUSTOM_JAR,Name="${app.name}",Jar="command-runner.jar",ActionOnFailure=TERMINATE_CLUSTER,Args=["spark-submit","--deploy-mode","cluster","--class","${jobRep.name}","s3://${aws.bucket.name}/${jar.name}","s3://${aws.bucket.name}/${aws.input}","s3://${aws.bucket.name}/${aws.output}","${max-filter}","${jobTypeRDD}"] \
		--log-uri s3://${aws.bucket.name}/${aws.log.dir} \
		--use-default-roles \
		--enable-debugging \
		--auto-terminate

# Main EMR launch using Replicated Join on Data Frame
aws-rep-df: jar upload-app-aws delete-output-aws
	aws emr create-cluster \
		--name "Triangle Spark Cluster Replicated Join DF, MAX=${max-filter},${aws.instance.type},NODE=${aws.num.nodes}" \
		--release-label ${aws.emr.release} \
		--instance-groups '[{"InstanceCount":${aws.num.nodes},"InstanceGroupType":"CORE","InstanceType":"${aws.instance.type}"},{"InstanceCount":1,"InstanceGroupType":"MASTER","InstanceType":"${aws.instance.type}"}]' \
	    --applications Name=Hadoop Name=Spark \
		--steps Type=CUSTOM_JAR,Name="${app.name}",Jar="command-runner.jar",ActionOnFailure=TERMINATE_CLUSTER,Args=["spark-submit","--deploy-mode","cluster","--class","${jobRep.name}","s3://${aws.bucket.name}/${jar.name}","s3://${aws.bucket.name}/${aws.input}","s3://${aws.bucket.name}/${aws.output}","${max-filter}","${jobTypeDF}"] \
		--log-uri s3://${aws.bucket.name}/${aws.log.dir} \
		--use-default-roles \
		--enable-debugging \
		--auto-terminate

# Main EMR launch using Reduce Join on RDD
aws-reduce-rdd: jar upload-app-aws delete-output-aws
	aws emr create-cluster \
		--name "Triangle Spark Cluster Reduce Join RDD, MAX=${max-filter},${aws.instance.type},NODE=${aws.num.nodes}" \
		--release-label ${aws.emr.release} \
		--instance-groups '[{"InstanceCount":${aws.num.nodes},"InstanceGroupType":"CORE","InstanceType":"${aws.instance.type}"},{"InstanceCount":1,"InstanceGroupType":"MASTER","InstanceType":"${aws.instance.type}"}]' \
	    --applications Name=Hadoop Name=Spark \
		--steps Type=CUSTOM_JAR,Name="${app.name}",Jar="command-runner.jar",ActionOnFailure=TERMINATE_CLUSTER,Args=["spark-submit","--deploy-mode","cluster","--class","${jobReduce.name}","s3://${aws.bucket.name}/${jar.name}","s3://${aws.bucket.name}/${aws.input}","s3://${aws.bucket.name}/${aws.output}","${max-filter}","${jobTypeRDD}"] \
		--log-uri s3://${aws.bucket.name}/${aws.log.dir} \
		--use-default-roles \
		--enable-debugging \
		--auto-terminate

# Main EMR launch using Reduce Join on Data Frame
aws-reduce-df: jar upload-app-aws delete-output-aws
	aws emr create-cluster \
		--name "Triangle Spark Cluster Reduce Join DF, MAX=${max-filter},${aws.instance.type},NODE=${aws.num.nodes}" \
		--release-label ${aws.emr.release} \
		--instance-groups '[{"InstanceCount":${aws.num.nodes},"InstanceGroupType":"CORE","InstanceType":"${aws.instance.type}"},{"InstanceCount":1,"InstanceGroupType":"MASTER","InstanceType":"${aws.instance.type}"}]' \
	    --applications Name=Hadoop Name=Spark \
		--steps Type=CUSTOM_JAR,Name="${app.name}",Jar="command-runner.jar",ActionOnFailure=TERMINATE_CLUSTER,Args=["spark-submit","--deploy-mode","cluster","--class","${jobReduce.name}","s3://${aws.bucket.name}/${jar.name}","s3://${aws.bucket.name}/${aws.input}","s3://${aws.bucket.name}/${aws.output}","${max-filter}","${jobTypeDF}"] \
		--log-uri s3://${aws.bucket.name}/${aws.log.dir} \
		--use-default-roles \
		--enable-debugging \
		--auto-terminate
		
# Download output from S3.
download-output-aws: clean-local-output
	mkdir ${local.output}
	aws s3 sync s3://${aws.bucket.name}/${aws.output} ${local.output}

# Change to standalone mode.
switch-standalone:
	cp config/standalone/*.xml ${hadoop.root}/etc/hadoop

# Change to pseudo-cluster mode.
switch-pseudo:
	cp config/pseudo/*.xml ${hadoop.root}/etc/hadoop

# Package for release.
distro:
	rm -f Spark-Twitter-Count.tar.gz
	rm -f Spark-Twitter-Count.zip
	rm -rf build
	mkdir -p build/deliv/Spark-Twitter-Count
	cp -r src build/deliv/Spark-Twitter-Count
	cp -r config build/deliv/Spark-Twitter-Count
	cp -r input build/deliv/Spark-Twitter-Count
	cp pom.xml build/deliv/Spark-Twitter-Count
	cp Makefile build/deliv/Spark-Twitter-Count
	cp README.txt build/deliv/Spark-Twitter-Count
	tar -czf Spark-Twitter-Count.tar.gz -C build/deliv Spark-Twitter-Count
	cd build/deliv && zip -rq ../../Spark-Twitter-Count.zip Spark-Twitter-Count

# Package for homework submission.
distro-submission:
	rm -f Spark-Twitter-Count.tar.gz
	rm -f Spark-Twitter-Count.zip
	rm -rf build
	mkdir -p build/deliv/Spark-Twitter-Count
	cp -r src build/deliv/Spark-Twitter-Count
	cp -r config build/deliv/Spark-Twitter-Count
	cp -r output build/deliv/Spark-Twitter-Count
	cp pom.xml build/deliv/Spark-Twitter-Count
	cp Makefile build/deliv/Spark-Twitter-Count
	cp README.txt build/deliv/Spark-Twitter-Count
	cp *.pdf build/deliv/Spark-Twitter-Count
	tar -czf Spark-Twitter-Count.tar.gz -C build/deliv Spark-Twitter-Count
	cd build/deliv && zip -rq ../../Spark-Twitter-Count.zip Spark-Twitter-Count