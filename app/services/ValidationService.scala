/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.google.inject.Inject
import com.networknt.schema.{Schema, SchemaRegistry, SpecificationVersion}
import play.api.Configuration
import play.api.libs.json.{JsValue, Json}

import scala.jdk.CollectionConverters.*

class Validator(schema: Schema) {

  def validate(jsValue: JsValue): List[String] = {

    val mapper = JsonMapper.builder().build()
    val json   = mapper.readTree(Json.stringify(jsValue))
    val result = schema.validate(json)

    if (result.isEmpty) {
      List.empty
    } else {
      result.iterator.asScala.toList.map {
        _.getMessage
      }
    }
  }
}

class ValidationService @Inject() (resourceService: ResourceService, config: Configuration) {

  private val schemaMapper: ObjectMapper = new ObjectMapper()

  private val schemaRegistry: SchemaRegistry =
    SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_4)

  def get(schemaName: String): Validator = {

    val schemaJson = schemaMapper.readTree(resourceService.getFile(s"schemas/$schemaName"))
    val schema     = schemaRegistry.getSchema(schemaJson)

    new Validator(schema)
  }

  def validator(schemaVersion: String): Validator = {
    val schema = config.get[String](s"declarations.schemas.$schemaVersion")
    get(schema)
  }
}
