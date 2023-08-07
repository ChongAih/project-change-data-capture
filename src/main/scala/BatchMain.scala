// spark-shell
import org.apache.hudi.DataSourceWriteOptions._
import org.apache.hudi.QuickstartUtils._
import org.apache.spark.SparkConf
import org.apache.spark.sql.SaveMode._
import org.apache.spark.sql.SparkSession

import scala.collection.JavaConversions._

object BatchMain {
  def main(args: Array[String]): Unit = {
    //    run(args)

    val tableName = "hudi_trips_cow"
    val basePath = "file:///tmp/hudi_trips_cow"
    val dataGen = new DataGenerator

    val sparkConf = {
      val conf = new SparkConf()
      conf.set("spark.app.name", "abc")
      conf.set("spark.sql.extensions", "org.apache.spark.sql.hudi.HoodieSparkSessionExtension")
      conf.set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      conf.set("spark.master", "local[2]")
      conf.set("spark.driver.bindAddress", "localhost")
    }

    val spark = SparkSession
      .builder
      .config(sparkConf)
      .enableHiveSupport()
      .getOrCreate()

    // Simulate initial data load - new parquet files created
    val inserts0 = convertToStringList(dataGen.generateInserts(100))
    val df0 = spark.read.json(spark.sparkContext.parallelize(inserts0, 2))
    df0.write.format("hudi").
      options(getQuickstartWriteConfigs).
      option(PRECOMBINE_FIELD_OPT_KEY, "ts").
      option(RECORDKEY_FIELD_OPT_KEY, "uuid").
      option(PARTITIONPATH_FIELD_OPT_KEY, "partitionpath").
      option("hoodie.table.name", tableName).
      option("hoodie.parquet.max.file.size", "30000").
      mode(Overwrite).
      save(basePath)

    // Simulate new data insert - new parquet files created
    val inserts = convertToStringList(dataGen.generateInserts(100))
    val df = spark.read.json(spark.sparkContext.parallelize(inserts, 2))
    df.write.format("hudi").
      options(getQuickstartWriteConfigs).
      option(PRECOMBINE_FIELD_OPT_KEY, "ts").
      option(RECORDKEY_FIELD_OPT_KEY, "uuid").
      option(PARTITIONPATH_FIELD_OPT_KEY, "partitionpath").
      option("hoodie.table.name", tableName).
      option("hoodie.parquet.max.file.size", "30000").
      mode(Append).
      save(basePath)

    // There should be 200 rows of record
    spark.read.
      format("hudi").
      load(basePath).show()

    // Check number of parquet file - should be 4 parquet files at the path
    Thread.sleep(5000)

    // Simulate partial update - new parquet files will be generated for the affected files
    val updates = convertToStringList(dataGen.generateUpdates(100))
    val df2 = spark.read.json(spark.sparkContext.parallelize(updates, 30))
    df2.limit(10).write.format("hudi").
      options(getQuickstartWriteConfigs).
      option(PRECOMBINE_FIELD_OPT_KEY, "ts").
      option(RECORDKEY_FIELD_OPT_KEY, "uuid").
      option(PARTITIONPATH_FIELD_OPT_KEY, "partitionpath").
      option("hoodie.table.name", tableName).
      option("hoodie.parquet.max.file.size", "30000").
      mode(Append).
      save(basePath)

    // There should be still 200 rows of record
    println(spark.read.
      format("hudi").
      load(basePath).count())

    // There should be 3 different commit times - 2 for insert, 1 for upsert
    spark.read.
      format("hudi").
      load(basePath).select("_hoodie_commit_time").distinct().show()
  }
}