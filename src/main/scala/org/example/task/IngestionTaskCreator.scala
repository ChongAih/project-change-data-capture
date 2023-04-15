package org.example.task

import com.typesafe.config.Config
import org.apache.hudi.DataSourceReadOptions
import org.apache.log4j.Logger
import org.apache.spark.SparkConf
import org.apache.spark.sql.functions.{col, lit}
import org.apache.spark.sql.streaming.{StreamingQuery, Trigger}
import org.apache.spark.sql.{DataFrame, Encoder, Encoders, SparkSession}
import org.example.model.Value
import org.example.util.{ConfigReader, Const, KafkaHelper, MapperHelper}
import org.apache.hadoop.fs.{FileSystem, Path}

import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.reflect.runtime.universe._
import scala.reflect.ClassTag
import java.io.File
import scala.util.matching.Regex

object IngestionTaskCreator {

  // No need to serialize and deserialize
  @transient private lazy val logger: Logger= Logger.getLogger(this.getClass.getSimpleName)

  def main(args: Array[String]): Unit = {
    val sparkConf = {
      val conf = new SparkConf()
      conf.set("spark.master", "local[2]")
      conf.set("spark.driver.bindAddress", "localhost")
      conf.set("hive.metastore.uris", "thrift://localhost:9083")
      conf.set("spark.sql.warehouse.dir", "/users/hive/warehouse")
      conf
    }
    val spark = SparkSession
      .builder
      .config(sparkConf)
      .getOrCreate()
    // Snapshot query
    spark
      .read
      .format("hudi")
      .load("/tmp/hudi_auth_users_cow")
      .show()

    // Incremental
    spark.read
      .format("org.apache.hudi")
      .option(DataSourceReadOptions.QUERY_TYPE_OPT_KEY, DataSourceReadOptions.QUERY_TYPE_INCREMENTAL_OPT_VAL)
      .option(DataSourceReadOptions.BEGIN_INSTANTTIME_OPT_KEY, "20230412000000")
      //.option(DataSourceReadOptions.BEGIN_INSTANTTIME_OPT_KEY, "20230413000000")
      //.option(DataSourceReadOptions.INCR_PATH_GLOB_OPT_KEY, "/ID") // Optional, use glob pattern if querying certain partitions
      .load("/tmp/hudi_auth_users_cow")
      .show()
//    spark.sql("select * from hudi_auth_users_cow").show()
  }

  def createIngestionTask[T <: Product : ClassTag: TypeTag](
                                                             config: Config, local: Boolean, hudi: Boolean,
                                                             kafkaStartTime: Long, kafkaEndTime: Long
                                                           ): Unit = {
    // Create SparkSession with Hive metastore
    val spark = getSparkSession(config, local)

    // Restart from checkpoint if exists else restart from specified offset
    val srcDF = getKafkaSrcDataFrame(spark, config, kafkaStartTime, kafkaEndTime)
    srcDF.printSchema()

    // Deserialize JSON data and convert to target case class
    val df = deserializeDataFrame[T](srcDF)

    // Process dataframe to mark row to be removed during compaction
    val dstDF = processDataFrame(df, spark)

    if (hudi) {
      postDataFrameToHudi(dstDF, config)
    } else {
      postDataFrameToConsole(dstDF, config)
    }
  }

  private def getSparkSession(config: Config, local: Boolean): SparkSession = {
    val appName = ConfigReader.getConfigField[String](config, Const.CONFIG_HUDI_APP_NAME)
    val hiveMetastoreURIs = ConfigReader.getConfigField[String](config, Const.CONFIG_HUDI_HIVE_METASTORE_URIS)

    val sparkConf = {
      val conf = new SparkConf()
      conf.set("spark.app.name", appName)
      conf.set("hive.metastore.uris", hiveMetastoreURIs)
      conf.set("spark.sql.warehouse.dir", "/users/hive/warehouse")
      conf.set("spark.serializer", "org.apache.spark.serializer.KryoSerializer") // hudi only accept Kryo
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

  private def getKafkaSrcDataFrame(sparkSession: SparkSession, config: Config,
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

  // TypeTag solves the problem that Scala's types are erased at runtime (type erasure)
  // Since a manifest needs to be created, it is necessary to interoperate with the type tag `evidence$1` in scope.
  // however typetag -> manifest conversion requires a class tag for the corresponding type to be present
  // [T <: Product: ClassTag: TypeTag] --> subclass of Product and provide ClassTag and TypeTag information
  private def deserializeDataFrame[T <: Product : ClassTag: TypeTag](srcDF: DataFrame): DataFrame = {

    // Create a customized de/serialization encoder as it is not available in spark.implicits._
    implicit val encoder: Encoder[Value[T]] = Encoders.product[Value[T]]

    val df = srcDF.mapPartitions(rows => {
      // Initialize a mapper in each executor/ partition
      val mapperHelper = new MapperHelper()
      rows.flatMap(row => {
        try {
          val valueJson = row.getAs[String](Const.KAFKA_VALUE_NAME)
          // Delete a row will return null content
          if (valueJson != null) {
            val value = mapperHelper.unmarshalNested[Value[T]](valueJson)
            List(value)
          } else {
            List()
          }
        } catch {
          case e: Exception =>
            logger.info(s"Exception occurs when parsing json: $e")
            List()
        }
      })
    }).toDF()
      .select(Const.PAYLOAD_COLUMN)

    df.printSchema()

    df
  }

  private def processDataFrame(df: DataFrame, spark: SparkSession): DataFrame = {
    // Broadcast variable
    val broadcastUpsertOp = spark.sparkContext.broadcast(Const.UPSERT_OP)
    val broadcastDeleteOp = spark.sparkContext.broadcast(Const.DELETE_OP)

    // Insert column to mark records to be removed during compaction
    val upsertDF = df
      .filter(col(Const.OP_COLUMN).isin(broadcastUpsertOp.value: _*))
      .select(Const.AFT_COLUMN)
      .withColumn("_hoodie_is_deleted", lit(false))
    val deleteDF = df
      .filter(col(Const.OP_COLUMN).isin(broadcastDeleteOp.value: _*))
      .select(Const.BEF_COLUMN)
      .withColumn("_hoodie_is_deleted", lit(true))
    val dstDF = upsertDF.union(deleteDF)

    dstDF.printSchema()

    dstDF
  }
  
  private def postDataFrameToConsole(dstDF: DataFrame, config: Config): Unit = {
    val triggerInterval = ConfigReader.getConfigField[String](config, Const.CONFIG_HUDI_KAFKA_TRIGGER_INTERVAL)

    val queryWriter = dstDF.writeStream
      .format("console")
      .outputMode("append")
      .option("truncate", "false")
      .trigger(Trigger.ProcessingTime(triggerInterval))

    while (true) {
      val query: StreamingQuery = queryWriter.start()
      if (config.hasPath(Const.CONFIG_HUDI_KAFKA_INPUT_REFRESH_INTERVAL)) {
        val refreshIntervalMs = ConfigReader.getConfigField[Long](config, Const.CONFIG_HUDI_KAFKA_INPUT_REFRESH_INTERVAL)
        query.awaitTermination(refreshIntervalMs)
        logger.info(s"Restarting query after $refreshIntervalMs ms")
        query.stop()
      } else {
        query.awaitTermination()
      }
    }
  }

  private def postDataFrameToHudi(dstDF: DataFrame, config: Config): Unit = {
    // Get configuration
    val triggerInterval = ConfigReader.getConfigField[String](config, Const.CONFIG_HUDI_KAFKA_TRIGGER_INTERVAL)
    val hudiConfig = config.getConfig("hudi.config")
    val sparkOptions: java.util.Map[String, String] = new java.util.HashMap()
    hudiConfig.entrySet().asScala.foreach(item => {
      sparkOptions.put(item.getKey, hudiConfig.getString(item.getKey))
    })

    // Split dataframe for different operations
    val queryWriter = dstDF.writeStream
      .format("hudi")
      .option("hoodie.datasource.write.operation", "upsert")
      .options(sparkOptions)
      .trigger(Trigger.ProcessingTime(triggerInterval))

    // Execute operation
    while (true) {
      val query: StreamingQuery = queryWriter.start()
      if (config.hasPath(Const.CONFIG_HUDI_KAFKA_INPUT_REFRESH_INTERVAL)) {
        val refreshIntervalMs = ConfigReader.getConfigField[Long](config, Const.CONFIG_HUDI_KAFKA_INPUT_REFRESH_INTERVAL)
        query.awaitTermination(refreshIntervalMs)
        logger.info(s"Restarting query after $refreshIntervalMs ms")
        query.stop()
      } else {
        query.awaitTermination()
      }
    }
  }

  private def createDirectory(spark: SparkSession, hudiTablePath: String): Unit = {
    val regex: Regex = "([a-zA-Z]+):(\\/{2}?)(.+)".r
    val path: String = regex.findAllMatchIn(hudiTablePath).toList.head.group(3)
    if (path.startsWith("file")) {
      val directory = new File(path)
      if (!directory.exists()) {
        directory.mkdir()
      }
    }
    else {
      val path: Path = new Path(hudiTablePath)
      val fileSystem = FileSystem.get(spark.sparkContext.hadoopConfiguration)
      fileSystem.mkdirs(path);
    }
  }
}