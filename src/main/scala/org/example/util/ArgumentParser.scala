package org.example.util

import org.rogach.scallop.{ScallopConf, ScallopOption}

class ArgumentParser(args: Array[String]) extends ScallopConf(args) {

  val task: ScallopOption[String] = opt[String]("task", required = true, descr = "task to be executed")
  val configPath: ScallopOption[String] = opt[String]("config-path", required = true,
    descr = "path to the configuration entailing all the configuration")
//  val kafkaStartTime: ScallopOption[String] = opt[String]()
//  val kafkaEndTime: ScallopOption[String] = opt[String]()
//  val local: ScallopOption[Boolean] = opt[Boolean]()

  verify()
}
