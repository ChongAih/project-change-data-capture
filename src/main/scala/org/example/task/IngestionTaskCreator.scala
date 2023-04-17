package org.example.task

import com.typesafe.config.Config
import org.apache.hudi.DataSourceReadOptions
import org.apache.log4j.Logger
import org.apache.spark.SparkConf
import org.apache.spark.sql.functions.{col, lit}
import org.apache.spark.sql.streaming.{StreamingQuery, Trigger}
import org.apache.spark.sql.{DataFrame, Encoder, Encoders, SparkSession}
import org.example.model.Value
import org.example.util.{ConfigReader, Const, KafkaHelper, MapperHelper, SparkHelper}
import org.apache.hadoop.fs.{FileSystem, Path}

import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.reflect.runtime.universe._
import scala.reflect.ClassTag
import java.io.File
import scala.util.matching.Regex

object IngestionTaskCreator {

  // No need to serialize and deserialize
  @transient private lazy val logger: Logger= Logger.getLogger(this.getClass.getSimpleName)

  def createIngestionTask[T <: Product : ClassTag: TypeTag](
                                                             config: Config, local: Boolean, hudi: Boolean,
                                                             kafkaStartTime: Long, kafkaEndTime: Long
                                                           ): Unit = {
    // Create SparkSession with Hive metastore
    val spark = SparkHelper.getSparkSession(config, local, hudi)

    // Restart from checkpoint if exists else restart from specified offset
    val srcDF = SparkHelper.getKafkaSrcDataFrame(spark, config, kafkaStartTime, kafkaEndTime)
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