## Introduction:

This project is a proof of concept demonstrating how Apache Beam can be used as a unified model for defining both batch and streaming data-parallel processing pipelines. Beam supports execution of pipeline on various runtime environments including Apache Apex, Apache Flink, Apache Spark, and Google Cloud Dataflow. This project uses Apache Flink as Beam's distributed processing backend.


## Install and Run Apache Flink

Download Apache Flink at https://flink.apache.org/downloads.html

Go to installation directory and run the below command
cd < Flink Home >/bin
$./bin/start-cluster.sh

Once started, by default flink dashboard can be accessed at http://localhost:8081

## Clone the project

Run the below commands to clone the project that contains Topic Subscriber and Beam Processor that streams the data to Flink.
$git clone https://github.com/solacese/beam_poc.git
$cd trade_beam/

## Run Beam Stream Processor

Execute the following maven command:

$mvn compile exec:java -Dexec.mainClass=< class name > -Dexec.args=' < solace cloud hostname > < username >@< message vpn name > < password > --runner=FlinkRunner --flinkMaster=< flink host >:< flink port >' -Pflink-runner

Example:

$mvn compile exec:java -Dexec.mainClass=com.trade.market.TopicSubscriberBeam -Dexec.args='vmr-mr8v6yiwicdj.messaging.solace.cloud:20512 solace-cloud-client@msgvpn-rwtxvklq4sp kasaov362vnboas6r1oi2v85q8 --runner=FlinkRunner --flinkMaster=localhost:8081' -Pflink-runner

Check the Flink dashboard for the jobs that are submitted. Jobs that are in process will appear in the "Running Jobs" section. Once completed, they can be seen in "Completed Jobs".

The flink runner parses the subscription message and compares the data (in this case, stocks security) and writes the transformed data to a file (trade_report) in the project directory. 
###### In a nutshell, the process includes the following steps:
    - Creating the Pipeline
    - Applying transforms to the Pipeline
    - Reading input (Subscription Message and Previous day intraday stock details, in this case)
    - Applying ParDo transforms
    - Writing output (Stock Performance as compared to previous day.)
    - Running the Pipeline

Host the output file (in this case, trade_report) on HTTP server so that it is accessible to other web components.

To run the beam processor on default runner, you can execute the following command which does not use any flink arguments:

$mvn compile exec:java -Dexec.mainClass=< class name > -Dexec.args=' < solace cloud hostname > < username >@< message vpn name > < password >'

Example:

$mvn compile exec:java -Dexec.mainClass=com.trade.market.TopicSubscriberBeam -Dexec.args='vmr-mr8v6yiwicdj.messaging.solace.cloud:20512 solace-cloud-client@msgvpn-rwtxvklq4sp kasaov362vnboas6r1oi2v85q8'

$npm install -g http-server 

###### Run the below command in the same directory as trade_report
$http-server -p 3000 --cors

###### Navigate to the following URL on browser to display the results on web browser:
http://localhost:3000/index.html
