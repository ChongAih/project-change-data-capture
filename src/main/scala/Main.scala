import org.apache.log4j.Logger
import org.example.model.auth.Users
import org.example.task.{ConnectorCreator, IngestionTaskCreator}
import org.example.util.{ArgumentParser, ConfigReader, Const}

object Main extends Runner {
  def main(args: Array[String]): Unit = {
//    run(args)
    run(Array(
      "--task", "create",
      "--config-path", "config/auth/users.conf",
      "--kafka-start-time", "-2",
      "--kafka-end-time", "-1",
      "--local", "--hudi"
    ))
  }
}

trait Runner {

  val logger: Logger= Logger.getLogger(this.getClass.getSimpleName)

  def run(args: Array[String]): Unit = {
    // Read command line arguments
    val argumentParser = new ArgumentParser(args)
    val task = argumentParser.task
      .getOrElse(throw new IllegalArgumentException("Missing --task"))
    val configPath = argumentParser.configPath
      .getOrElse(throw new IllegalArgumentException("Missing --config-path"))
    val kafkaStartTime = argumentParser.kafkaStartTime
        .getOrElse(Const.KAFKA_DEFAULT_LATEST_OFFSET).toLong
    val kafkaEndTime = argumentParser.kafkaEndTime
        .getOrElse(Const.KAFKA_DEFAULT_LATEST_OFFSET).toLong
    val local = argumentParser.local
      .getOrElse(Const.SPARK_LOCAL_MASTER)
    val hudi = argumentParser.hudi
      .getOrElse(Const.SPARK_WRITE_TO_HUDI)

    logger.info(
      s"""User set parameters:
         |  * task: $task
         |  * configPath: $configPath
         |  * kafkaStartTime: $kafkaStartTime
         |  * kafkaEndTime: $kafkaEndTime
         |  * local: $local
         |  * hudi: $hudi
         |""".stripMargin)

    // Read configuration
    val config = ConfigReader.readConfig(configPath)

    if (task == "create") {
      // Create Debezium connector
      ConnectorCreator.createConnector(config)

      // Create ingestion task
      val projectClassName = ConfigReader.getConfigField[String](config, Const.PROJECT_CLASS_NAME)
      projectClassName match {
        case "Users" => IngestionTaskCreator.createIngestionTask[Users](config, local, hudi, kafkaStartTime, kafkaEndTime)
      }
    }
  }

}