package org.example.util

object Const {
  val FALLBACK_CONFIG_PATH = "config/default.conf" // Note the backslash

  val CONNECTOR_TEMPLATE_PATH = "/template/connector.json"
  val CONNECTOR_TEMPLATE_CONFIG = "config"
  val CONNECTOR_TEMPLATE_NAME = "name"
  val CONNECTOR_TEMPLATE_CONFIG_DB_HOSTNAME = "database.hostname"
  val CONNECTOR_TEMPLATE_CONFIG_DB_PORT = "database.port"
  val CONNECTOR_TEMPLATE_CONFIG_DB_USER = "database.user"
  val CONNECTOR_TEMPLATE_CONFIG_DB_PASSWORD = "database.password"
  val CONNECTOR_TEMPLATE_CONFIG_DB_SERVER_ID = "database.server.id"
  val CONNECTOR_TEMPLATE_CONFIG_DB_INCLUDE_LIST = "database.include.list"
  val CONNECTOR_TEMPLATE_CONFIG_TOPIC_PREFIX = "topic.prefix"
  val CONNECTOR_TEMPLATE_CONFIG_KAFKA_BOOTSTRAP_SERVERS = "schema.history.internal.kafka.bootstrap.servers"

  val CONFIG_CONNECTOR_NAME = "connector.name"
  val CONFIG_DB_HOSTNAME = "connector.database.hostname"
  val CONFIG_DB_PORT = "connector.database.port"
  val CONFIG_DB_USER = "connector.database.user"
  val CONFIG_DB_PASSWORD = "connector.database.password"
  val CONFIG_DB_SERVER_ID = "connector.database.server_id"
  val CONFIG_DB_DB_INCLUDE_LIST = "connector.database.db"
  val CONFIG_KAFKA_TOPIC_PREFIX = "connector.kafka.topic_prefix"
  val CONFIG_KAFKA_BOOTSTRAP_SERVERS = "connector.kafka.bootstrap_servers"

  val HTTP_CONNECTOR_CREATE = "http.connect.create"
}
