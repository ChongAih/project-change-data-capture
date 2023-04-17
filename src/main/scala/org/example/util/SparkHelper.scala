package org.example.util

import com.typesafe.config.Config
import org.apache.log4j.Logger
import org.apache.spark.SparkConf
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions.col

object SparkHelper {

  // No need to serialize and deserialize
  @transient private lazy val logger: Logger = Logger.getLogger(this.getClass.getSimpleName)

  def getSparkSession(config: Config, local: Boolean, hudi: Boolean): SparkSession = {
    val appName = ConfigReader.getConfigField[String](config, Const.CONFIG_HUDI_APP_NAME)
    val hiveMetastoreURIs = ConfigReader.getConfigField[String](config, Const.CONFIG_HUDI_HIVE_METASTORE_URIS)

    val sparkConf = {
      val conf = new SparkConf()
      conf.set("spark.app.name", appName)
      conf.set("hive.metastore.uris", hiveMetastoreURIs)
      conf.set("spark.sql.warehouse.dir", "/users/hive/warehouse")
      if (hudi) {
        // Hudi only accept Kryo
        conf.set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      }
      if (local) {
        conf.set("spark.master", "local[2]")
        conf.set("spark.driver.bindAddress", "localhost")
      }
      conf
    }

    SparkSession
      .builder
      .config(sparkConf)
      .enableHiveSupport()
      .getOrCreate()
  }

  def getKafkaSrcDataFrame(sparkSession: SparkSession, config: Config,
                                   kafkaStartTime: Long, kafkaEndTime: Long): DataFrame = {
    val kafkaSrcTopics = ConfigReader.getConfigField[String](config, Const.CONFIG_HUDI_KAFKA_INPUT_TOPIC)
    val kafkaSrcServers = ConfigReader.getConfigField[String](config, Const.CONFIG_HUDI_KAFKA_INPUT_BOOTSTRAP_SERVERS)
    val kafkaMaxTriggerOffset = ConfigReader.getConfigField[Long](config, Const.CONFIG_HUDI_KAFKA_INPUT_MAX_TRIGGER_OFFSETS)
    val aclSrc = ConfigReader.getConfigField[Boolean](config, Const.CONFIG_HUDI_KAFKA_INPUT_ACL)
    val securityProtocol = ConfigReader.getConfigField[String](config, Const.CONFIG_HUDI_KAFKA_COMMON_SECURITY_PROTOCOL)
    val saslMechanism = ConfigReader.getConfigField[String](config, Const.CONFIG_HUDI_KAFKA_COMMON_SASL_MECHANISM)

    val startingOffsets = KafkaHelper.getKafkaStartingOffsets(kafkaSrcServers, kafkaSrcTopics,
      kafkaStartTime, aclSrc, securityProtocol, saslMechanism)
    val endingOffsets = KafkaHelper.getKafkaEndingOffsets(kafkaSrcServers, kafkaSrcTopics,
      kafkaEndTime, aclSrc, securityProtocol, saslMechanism)

    logger.info(s"kafkaSrcServers: $kafkaSrcServers; kafkaSrcTopics: $kafkaSrcTopics")
    logger.info(s"startingOffsets: $startingOffsets; endingOffsets: $endingOffsets")

    val df: DataFrame = kafkaEndTime match {
      // Batch query
      case e if e > kafkaStartTime && e > 0 => {
        var temp = sparkSession
          .read
          .format("kafka")
          .option("kafka.bootstrap.servers", kafkaSrcServers)
          //.option("kafka.group_id", kafkaGroupId) // Generated automatically if not given
          .option("subscribe", kafkaSrcTopics)
          .option("failOnDataLoss", "false") // Do not fail job even if Kafka offset has been removed
          .option("startingOffsets", startingOffsets)
          .option("endingOffsets", endingOffsets)
          .option("maxOffsetsPerTrigger", kafkaMaxTriggerOffset)
        temp = if (aclSrc) {
          temp.option("kafka.sasl.mechanism", saslMechanism)
            .option("kafka.security.protocol", securityProtocol)
        } else {
          temp
        }
        temp.load()
      }
      // Stream query
      case _ => {
        var temp = sparkSession
          .readStream
          .format("kafka")
          .option("kafka.bootstrap.servers", kafkaSrcServers)
          .option("subscribe", kafkaSrcTopics)
          .option("failOnDataLoss", "false")
          .option("startingOffsets", startingOffsets)
          .option("maxOffsetsPerTrigger", kafkaMaxTriggerOffset)
        temp = if (aclSrc) {
          temp.option("kafka.sasl.mechanism", saslMechanism)
            .option("kafka.security.protocol", securityProtocol)
        } else {
          temp
        }
        temp.load()
      }
    }

    df.select(
      col(Const.KAFKA_VALUE_NAME).cast("string").as(Const.KAFKA_VALUE_NAME),
      col(Const.KAFKA_TIMESTAMP_NAME).cast("long").as(Const.KAFKA_TIMESTAMP_NAME)
    )
  }

}
