package org.example.model

case class Value[T](payload: Payload[T])

case class Payload[T](before: T, after: T, op: String)