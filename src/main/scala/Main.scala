import org.apache.log4j.Logger
import org.example.task.ConnectorCreator
import org.example.util.{ArgumentParser, ConfigReader}

object Main extends Runner {
  def main(args: Array[String]): Unit = {
    run(args)
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

    logger.info(
      s"""
         |User set parameters:
         |  * task: $task
         |  * configPath: $configPath
         |""".stripMargin)

    // Read configuration
    val config = ConfigReader.readConfig(configPath)
    println(config)

    // Create Debezium connector
    ConnectorCreator.createConnector(config)
  }

}