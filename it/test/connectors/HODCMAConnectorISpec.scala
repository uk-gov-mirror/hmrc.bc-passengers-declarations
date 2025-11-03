/*
 * Copyright 2025 HM Revenue & Customs
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

package connectors

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.http.Fault
import helpers.Constants
import models.CMASubmissionResponse
import models.declarations.Etmp
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.ContentTypes
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{await, *}
import play.api.test.Injecting
import utils.WireMockHelper

class HODCMAConnectorISpec
    extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite
    with WireMockHelper
    with ScalaFutures
    with IntegrationPatience
    with Injecting
    with Constants {

  override lazy val app: Application =
    new GuiceApplicationBuilder()
      .configure(
        "feature.isUsingCMA"                                          -> true,
        "microservice.services.des.cma.port"                          -> server.port(),
        "microservice.services.des.cma.circuit-breaker.max-failures"  -> 1,
        "microservice.services.des.cma.circuit-breaker.reset-timeout" -> "1 second"
      )
      .build()

  private def stubCall(data: JsObject = declarationData): MappingBuilder =
    post(urlEqualTo("/passengers/declarations/simpledeclaration/v1"))
      .withHeader(CONTENT_TYPE, matching(ContentTypes.JSON))
      .withHeader(ACCEPT, matching(ContentTypes.JSON))
      .withHeader("X-Correlation-ID", matching(correlationId))
      .withHeader("Authorization", matching("Bearer changeme"))
      .withHeader("X-Forwarded-Host", matching("MDTP"))
      .withHeader(DATE, matching("""^(Mon|Tue|Wed|Thu|Fri|Sat|Sun), \d{2} (Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) \d{4} \d{2}:\d{2}:\d{2} GMT$"""))
      .withRequestBody(equalTo(Json.stringify(Json.toJsObject(data.as[Etmp]))))

  private lazy val connector: HODConnector = inject[HODConnector]

  "HODConnector" when {
    "isUsingCMA is true" when {
      "call the HOD when declaration is submitted" in {

        server.stubFor(
          stubCall()
            .willReturn(aResponse().withStatus(NO_CONTENT))
        )

        await(connector.submit(declaration, isAmendment = false)) shouldBe CMASubmissionResponse.Submitted
      }

      "fall back to a SubmissionResponse.Error when the downstream call errors while submitting declaration" in {

        server.stubFor(
          stubCall()
            .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE))
        )

        await(connector.submit(declaration, isAmendment = false)) shouldBe CMASubmissionResponse.Error
      }

      "fail fast while the circuit breaker is open when declaration is submitted" in {

        server.stubFor(
          stubCall()
            .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE))
            .willReturn(aResponse().withStatus(NO_CONTENT))
        )

        await(connector.submit(declaration, isAmendment = false)) shouldBe CMASubmissionResponse.Error
        await(connector.submit(declaration, isAmendment = false)) shouldBe CMASubmissionResponse.Error

        Thread.sleep(2000)
        await(connector.submit(declaration, isAmendment = false)) shouldBe CMASubmissionResponse.Submitted
      }

      "call the HOD when amendment is submitted" in {

        server.stubFor(
          stubCall(amendmentData)
            .willReturn(aResponse().withStatus(NO_CONTENT))
        )

        await(connector.submit(amendment, isAmendment = true)) shouldBe CMASubmissionResponse.Submitted
      }

      "throw an exception when amendment is submitted but contains no correlation id" in {

        val amendmentWithNoAmendmentCorrelationId = amendment.copy(amendCorrelationId = None)

        val result = intercept[Exception] {
          connector.submit(amendmentWithNoAmendmentCorrelationId, isAmendment = true)
        }

        result.getMessage shouldBe "AmendCorrelation Id is empty"
      }

      "fall back to a SubmissionResponse.ParsingException when the declaration data is not complete" in {

        val missingDataDeclaration = declaration.copy(data = Json.obj())

        await(
          connector.submit(missingDataDeclaration, isAmendment = false)
        ) shouldBe CMASubmissionResponse.ParsingException
      }

      "fall back to a SubmissionResponse.ParsingException when the amendment data is not complete" in {

        val missingAmendmentDataDeclaration = amendment.copy(amendData = Some(Json.obj()))

        await(
          connector
            .submit(missingAmendmentDataDeclaration, isAmendment = true)
        ) shouldBe CMASubmissionResponse.ParsingException
      }

      "fall back to a SubmissionResponse.Error when the downstream call errors in amendments journey" in {

        server.stubFor(
          stubCall(amendmentData)
            .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE))
        )

        await(connector.submit(amendment, isAmendment = true)) shouldBe CMASubmissionResponse.Error
      }

      "fail fast while the circuit breaker is open in amendments journey" in {

        server.stubFor(
          stubCall(amendmentData)
            .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE))
            .willReturn(aResponse().withStatus(NO_CONTENT))
        )

        await(connector.submit(amendment, isAmendment = true)) shouldBe CMASubmissionResponse.Error
        await(connector.submit(amendment, isAmendment = true)) shouldBe CMASubmissionResponse.Error

        Thread.sleep(2000)
        await(connector.submit(amendment, isAmendment = true)) shouldBe CMASubmissionResponse.Submitted
      }
    }
  }
}
