package org.example.util

import com.typesafe.config.{Config, ConfigFactory}

import scala.reflect.runtime.universe._

object ConfigReader {
  val fallbackConfigPath: String = Const.FALLBACK_CONFIG_PATH
  val defaultConfig: Config = {
    ConfigFactory.parseResources(fallbackConfigPath)
  }

  def readConfig(configPath: String): Config = {
    val config: Config = {
      ConfigFactory.parseResources(configPath)
        .withFallback(defaultConfig)
        .resolve()
    }
    config
  }

  def getConfigField[T: TypeTag](config: Config, path: String): T = {
    if (config.hasPath(path)) {
      typeOf[T] match {
        case t if t =:= typeOf[String] => config.getString(path).asInstanceOf[T]
        case t if t =:= typeOf[Int] => config.getInt(path).asInstanceOf[T]
        case t if t =:= typeOf[Long] => config.getLong(path).asInstanceOf[T]
        case t if t =:= typeOf[Double] => config.getDouble(path).asInstanceOf[T]
        case t if t =:= typeOf[Boolean] => config.getBoolean(path).asInstanceOf[T]
      }
    } else {
      throw new Exception(s"Missing $path in configuration")
    }
  }
}
