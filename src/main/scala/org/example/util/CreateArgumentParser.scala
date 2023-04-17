package org.example.util

import org.rogach.scallop.{ScallopConf, ScallopOption}

class CreateArgumentParser(args: Array[String]) extends ScallopConf(args) {

  val configPath: ScallopOption[String] = opt[String]("config-path", required = true,
    descr = "path to the configuration entailing all the configuration")
  val kafkaStartTime: ScallopOption[String] = opt[String]("kafka-start-time", required = false,
    descr = "start time to consume the Kafka topic", default = Some("-1"))
  val kafkaEndTime: ScallopOption[String] = opt[String]("kafka-end-time", required = false,
    descr = "end time to consume the Kafka topic", default = Some("-1"))
  val local: ScallopOption[Boolean] = opt[Boolean]("local", required = false,
    descr = "Spark job running locally or cluster", default = Some(false))
  val writeToHudi: ScallopOption[Boolean] = opt[Boolean]("write-to-hudi", required = false,
    descr = "Write data to Hudi or console", default = Some(false))

  verify()
}
