package org.example.model.auth

// Case class by default extends scala.Product to conform with Spark Encoder requirements
case class Users(
                  username: String,
                  password: String,
                  enabled: Int,
                  ts: Long,
                  country: String
                )