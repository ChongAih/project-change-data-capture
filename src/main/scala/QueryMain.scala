import org.apache.hudi.DataSourceReadOptions
import org.apache.log4j.Logger
import org.apache.spark.sql.SparkSession
import org.example.util.{ConfigReader, Const, QueryArgumentParser, SparkHelper}

object QueryMain extends QueryRunner {
  def main(args: Array[String]): Unit = {
    //    run(args)

    // Snapshot query
    run(Array(
      "--config-path", "config/auth/users.conf",
      "--local"
    ))

    // Incremental query
    run(Array(
      "--config-path", "config/auth/users.conf",
      "--local",
      "--incremental",
      "--begin-instant-time", "20230731223350"
    ))

    // Time travel query - use the commitTime in .hoodie folder
    run(Array(
      "--config-path", "config/auth/users.conf",
      "--local",
      "--time-travel",
      "--commit-time", "20230731221704842"
    ))

    // Snapshot query
    run(Array(
      "--config-path", "config/auth/users_mor.conf",
      "--local"
    ))

    // Incremental query
    run(Array(
      "--config-path", "config/auth/users_mor.conf",
      "--local",
      "--incremental",
      "--begin-instant-time", "20230802092000000"
    ))

    // Time travel query - use the commitTime in .hoodie folder
    run(Array(
      "--config-path", "config/auth/users_mor.conf",
      "--local",
      "--time-travel",
      "--commit-time", "20230802091307840"
    ))
  }
}


trait QueryRunner {

  val logger: Logger= Logger.getLogger(this.getClass.getSimpleName)

  def run(args: Array[String]): Unit = {
    // Read command line arguments
    val argumentParser = new QueryArgumentParser(args)
    val configPath = argumentParser.configPath
      .getOrElse(throw new IllegalArgumentException("Missing --config-path"))
    val local = argumentParser.local
      .getOrElse(Const.SPARK_LOCAL_MASTER)
    val incremental = argumentParser.incremental
      .getOrElse(Const.SPARK_HUDI_READ_INCREMENTAL)
    val beginInstantTime = argumentParser.beginInstantTime
      .getOrElse(Const.SPARK_HUDI_BEGIN_INSTANT_TIME)
    val timeTravel = argumentParser.timeTravel
      .getOrElse(Const.SPARK_HUDI_READ_TIME_TRAVEL)
    val commitTime = argumentParser.commitTime
      .getOrElse(Const.SPARK_HUDI_TIME_TRAVEL_COMMIT_TIME)

    logger.info(
      s"""User set parameters:
         |  * configPath: $configPath
         |  * local: $local
         |  * incremental: $incremental
         |  * beginInstantTime: $beginInstantTime
         |  * timeTravel: $timeTravel
         |  * commitTime: $commitTime
         |""".stripMargin)

    // Read configuration
    val config = ConfigReader.readConfig(configPath)

    // Query
    var spark: SparkSession = null
    try {
      spark = SparkHelper.getSparkSession(config, local, Const.SPARK_WRITE_TO_HUDI)
      val path = ConfigReader.getConfigField[String](config, Const.CONFIG_HUDI_PATH)

      if (timeTravel) {
        logger.info("Issuing a snapshot time-travel query...")
        spark.read
          .format("org.apache.hudi")
          .option("as.of.instant", commitTime)
          .load(path)
          .show(false)
      } else {
        if (incremental) {
          logger.info("Issuing an incremental query...")
          // Incremental - part of the updated data since begin-instant-time
          spark.read
            .format("org.apache.hudi")
            .option("hoodie.datasource.query.type", DataSourceReadOptions.QUERY_TYPE_INCREMENTAL_OPT_VAL)
            .option("hoodie.datasource.read.begin.instanttime", beginInstantTime)
            .load(path)
            .show(false)
        } else {
          logger.info("Issuing a snapshot query...")
          // snapshot query - full updated data
          spark
            .read
            .format("hudi")
            .load(path)
            .show(false)
        }
      }


    } catch {
      case e: Exception =>
        logger.error(e)
    } finally {
      if (spark != null) {
        spark.close()
      }
    }
  }

}