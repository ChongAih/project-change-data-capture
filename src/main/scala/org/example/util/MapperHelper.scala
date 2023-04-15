package org.example.util

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.{DeserializationFeature, JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule

import scala.reflect.runtime.universe._

class MapperHelper extends Serializable {

  @transient private lazy val objectMapper: ObjectMapper = {
    val m = new ObjectMapper()
    m.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    m.registerModule(DefaultScalaModule)
  }

  def marshal(map: Any): String = {
    objectMapper.writeValueAsString(map)
  }

  def unmarshal[T](json: String, clazz: Class[T]): T = {
    val output = objectMapper.readValue(json, clazz)
    output
  }

  def unmarshal(json: String): JsonNode  = {
    objectMapper.readTree(json)
  }

  // Due to JavaType erasure during runtime, the T might be treated as 'T extends Object'
  // and Jackson will bind the JSON Objects as Map. Thus need to customize TypeReference
  def unmarshalNested[T: TypeTag](json: String)(implicit ev: Manifest[T]): T = {
    val value = objectMapper.readValue(json, new TypeReference[T] {
      // get the type information at runtime
      override def getType: java.lang.reflect.Type = typeFromManifest(manifest[T])
    })
    value
  }

  private def typeFromManifest(m: Manifest[_]): java.lang.reflect.Type = {
    if (m.typeArguments.isEmpty) {
      m.runtimeClass
    }
    else {
      new ParameterizedTypeImpl(m.runtimeClass, m.typeArguments.map(typeFromManifest).toArray, null)
    }
  }

  private class ParameterizedTypeImpl(
                                       raw: Class[_],
                                       args: Array[java.lang.reflect.Type],
                                       owner: java.lang.reflect.Type
                                     ) extends java.lang.reflect.ParameterizedType {
    def getRawType: Class[_] = raw
    def getActualTypeArguments: Array[java.lang.reflect.Type] = args
    def getOwnerType: java.lang.reflect.Type = owner
  }

}
