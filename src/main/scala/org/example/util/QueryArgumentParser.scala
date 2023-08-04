package org.example.util

import org.rogach.scallop.{ScallopConf, ScallopOption}

class QueryArgumentParser(args: Array[String]) extends ScallopConf(args) {

  val configPath: ScallopOption[String] = opt[String]("config-path", required = true,
    descr = "path to the configuration entailing all the configuration")
  val local: ScallopOption[Boolean] = opt[Boolean]("local", required = false,
    descr = "Spark job running locally or cluster", default = Some(false))
  val incremental: ScallopOption[Boolean] = opt[Boolean]("incremental", required = false,
    descr = "default is to read a snapshot (latest view of full data), set to True will read Hudi data " +
      "in an incremental manner (retrieve only the changes made to the data since the begin-instant-time - " +
      "only contain part of the data (those updated))", default = Some(false))
  val timeTravel: ScallopOption[Boolean] = opt[Boolean]("time-travel", required = false,
    descr = "default is to read a snapshot (latest view of full data), set to True will read data " +
      "of a certain commit up to the the commit-time - " +
      "only contain the committed data before the commit-time", default = Some(false))
  val beginInstantTime: ScallopOption[String] = opt[String]("begin-instant-time", required = false,
    descr = "Instant time of the committed files to start reading (format: YYYYMMDDHHmmSS)", default = Some("19700101000000"))
  val commitTime: ScallopOption[String] = opt[String]("commit-time", required = false,
    descr = "Instant time of the committed files to read until (format: YYYYMMDDHHmmSS)", default = Some("99990101000000"))

  verify()
}
