package org.example.util

object Const {
  val FALLBACK_CONFIG_PATH = "config/default.conf" // Note the backslash

  val PROJECT_CLASS_NAME = "project.class.name"

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
  val CONFIG_HUDI_APP_NAME = "hudi.app_name"
  val CONFIG_HUDI_HIVE_METASTORE_URIS = "hudi.hive.metastore.uris"
  val CONFIG_HUDI_KAFKA_INPUT_BOOTSTRAP_SERVERS = "hudi.kafka.input.bootstrap_servers"
  val CONFIG_HUDI_KAFKA_INPUT_TOPIC = "hudi.kafka.input.topic"
  val CONFIG_HUDI_KAFKA_INPUT_MAX_TRIGGER_OFFSETS = "hudi.kafka.input.max_trigger_offsets"
  val CONFIG_HUDI_KAFKA_INPUT_ACL = "hudi.kafka.input.acl"
  val CONFIG_HUDI_KAFKA_INPUT_REFRESH_INTERVAL = "hudi.kafka.input.refresh_interval"
  val CONFIG_HUDI_KAFKA_COMMON_SECURITY_PROTOCOL = "hudi.kafka.common.security_protocol"
  val CONFIG_HUDI_KAFKA_COMMON_SASL_MECHANISM = "hudi.kafka.common.sasl_mechanism"
  val CONFIG_HUDI_KAFKA_TRIGGER_INTERVAL = "hudi.kafka.trigger_interval"

  val KAFKA_DEFAULT_LATEST_OFFSET = "-1"
  val SPARK_LOCAL_MASTER = false
  val SPARK_WRITE_TO_HUDI = false
  val HTTP_CONNECTOR_CREATE = "http.connect.create"
  val KAFKA_VALUE_NAME = "value"
  val KAFKA_TIMESTAMP_NAME = "timestamp"
  val PAYLOAD_COLUMN = "payload.*"
  val OP_COLUMN = "op"
  val AFT_COLUMN = "after.*"
  val BEF_COLUMN = "before.*"
  val UPSERT_OP: List[String] = List("r", "c", "u")
  val DELETE_OP: List[String] = List("d")
}
