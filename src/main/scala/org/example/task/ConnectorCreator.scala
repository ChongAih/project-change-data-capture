package org.example.task

import com.sun.deploy.net.HttpResponse
import com.typesafe.config.Config
import org.apache.log4j.Logger
import org.example.util.{ConfigReader, Const}
import org.json.{JSONArray, JSONObject}

import java.io.{BufferedReader, DataOutputStream, InputStream, InputStreamReader, OutputStream}
import java.net.{HttpURLConnection, URL}
import java.util
import scala.collection.JavaConversions.{asScalaBuffer, asScalaSet}
import scala.collection.JavaConverters.asScalaBufferConverter

object ConnectorCreator {

  private lazy val logger: Logger= Logger.getLogger(this.getClass.getSimpleName)

  def createConnector(config: Config): Boolean = {
    val existingConnectors = getConnectors(config)
    val connectorName = getConnectorName(config)

    if (existingConnectors.contains(connectorName)) {
      logger.info("Connector already exist: " + connectorName)
      true
    } else {
      var connection: HttpURLConnection = null

      try {
        // https://stackoverflow.com/questions/1359689/how-to-send-http-request-in-java
        // Create connection
        val url: URL = new URL(ConfigReader.getConfigField[String](config, Const.HTTP_CONNECTOR_CREATE))
        connection = url.openConnection().asInstanceOf[HttpURLConnection]
        connection.setRequestMethod("POST")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.setDoOutput(true) // write to url connection

        // Create JSON for creating Debezium connector
        val connectorJsonStr = {
          val json = createConnectorJson(config)
          assert(json.isDefined)
          json.get
        }

        // Send request
        val outputStream: DataOutputStream = new DataOutputStream(connection.getOutputStream)
        outputStream.writeBytes(connectorJsonStr)
        outputStream.close()

        // Receive response
        val responseCode = connection.getResponseCode
        if (responseCode == HttpURLConnection.HTTP_CREATED) {
          val inputStream: InputStream = connection.getInputStream
          val bufferedReader: BufferedReader = new BufferedReader(new InputStreamReader(inputStream))
          val response = Stream
            .continually(bufferedReader.readLine())
            .takeWhile(_ != null)
            .mkString("")
          logger.info("Connector created with detail: " + response)
          true
        } else {
          val errorStream: InputStream = connection.getErrorStream
          val bufferedReader: BufferedReader = new BufferedReader(new InputStreamReader(errorStream))
          val error = Stream
            .continually(bufferedReader.readLine())
            .takeWhile(_ != null)
            .mkString("")
          throw new RuntimeException("HTTP error code: " + responseCode + "; error: " + error)
        }
      } catch {
        case e: Exception => {
          logger.error(e)
          false
        }
      } finally {
        if (connection != null) {
          connection.disconnect()
        }
      }
    }
  }

  def getConnectors(config: Config): Array[String] = {
    var connection: HttpURLConnection = null
    try {
      // https://stackoverflow.com/questions/1359689/how-to-send-http-request-in-java
      // Create connection
      val url: URL = new URL(ConfigReader.getConfigField[String](config, Const.HTTP_CONNECTOR_CREATE))
      connection = url.openConnection().asInstanceOf[HttpURLConnection]
      connection.setRequestMethod("GET")
      connection.setRequestProperty("Content-Type", "application/json")
      connection.setRequestProperty("Accept", "application/json")

      // Receive response
      val responseCode = connection.getResponseCode
      if (responseCode == HttpURLConnection.HTTP_OK) {
        val inputStream: InputStream = connection.getInputStream
        val bufferedReader: BufferedReader = new BufferedReader(new InputStreamReader(inputStream))
        val response = Stream
          .continually(bufferedReader.readLine())
          .takeWhile(_ != null)
          .mkString("")
        val connectorArray: util.ArrayList[String] = {
          val temp = new util.ArrayList[String]()
          val tempJson = new JSONArray(response)
          for (i <- 0 until tempJson.length()) {
            temp.add(tempJson.get(i).toString)
          }
          temp
        }
        logger.info("Existing connector: " + connectorArray)
        connectorArray.asScala.toArray
      } else {
        val errorStream: InputStream = connection.getErrorStream
        val bufferedReader: BufferedReader = new BufferedReader(new InputStreamReader(errorStream))
        val error = Stream
          .continually(bufferedReader.readLine())
          .takeWhile(_ != null)
          .mkString("")
        throw new RuntimeException("HTTP error code: " + responseCode + "; error: " + error)
      }
    } catch {
      case e: Exception => {
        logger.error(e)
        new Array[String](0)
      }
    } finally {
      if (connection != null) {
        connection.disconnect()
      }
    }
  }

  private def createConnectorJson(config: Config): Option[String] = {
    val connectorTemplatePath = Const.CONNECTOR_TEMPLATE_PATH

    try {
      // Read template connector JSON
      val bufferedReader = new BufferedReader(new InputStreamReader(
        this.getClass.getResourceAsStream(connectorTemplatePath)))
      val str = Stream
        .continually(bufferedReader.readLine())
        .takeWhile(_ != null)
        .mkString("")
      val connectorJson = new JSONObject(str)

      // Update content based on config
      updateConfig(connectorJson, config)

      Some(connectorJson.toString)
    } catch {
      case e: Exception => {
        logger.error(e)
        null
      }
    }
  }

  private def updateConfig(connectorJson: JSONObject, config: Config): JSONObject = {
    // Update name
    connectorJson.put(Const.CONNECTOR_TEMPLATE_NAME, getConnectorName(config))

    // Update config
    val configKeyMapping = new util.HashMap[String, String]() {
      put(Const.CONNECTOR_TEMPLATE_CONFIG_DB_HOSTNAME, Const.CONFIG_DB_HOSTNAME)
      put(Const.CONNECTOR_TEMPLATE_CONFIG_DB_PORT, Const.CONFIG_DB_PORT)
      put(Const.CONNECTOR_TEMPLATE_CONFIG_DB_USER, Const.CONFIG_DB_USER)
      put(Const.CONNECTOR_TEMPLATE_CONFIG_DB_PASSWORD, Const.CONFIG_DB_PASSWORD)
      put(Const.CONNECTOR_TEMPLATE_CONFIG_DB_SERVER_ID, Const.CONFIG_DB_SERVER_ID)
      put(Const.CONNECTOR_TEMPLATE_CONFIG_DB_INCLUDE_LIST, Const.CONFIG_DB_DB_INCLUDE_LIST)
      put(Const.CONNECTOR_TEMPLATE_CONFIG_TOPIC_PREFIX, Const.CONFIG_KAFKA_TOPIC_PREFIX)
      put(Const.CONNECTOR_TEMPLATE_CONFIG_KAFKA_BOOTSTRAP_SERVERS, Const.CONFIG_KAFKA_BOOTSTRAP_SERVERS)
    }
    val connectorConfigJson = connectorJson.getJSONObject(Const.CONNECTOR_TEMPLATE_CONFIG)
    configKeyMapping.entrySet().foreach(kv => {
      connectorConfigJson.put(kv.getKey, ConfigReader.getConfigField[String](config, kv.getValue))
    })
    connectorJson.put(Const.CONNECTOR_TEMPLATE_CONFIG, connectorConfigJson)
  }

  private def getConnectorName(config: Config): String = {
    return ConfigReader.getConfigField[String](config, Const.CONFIG_CONNECTOR_NAME)
  }

}
