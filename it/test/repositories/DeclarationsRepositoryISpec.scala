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

package repositories

import helpers.IntegrationSpecCommonBase
import models.declarations.{Declaration, State}
import models.{ChargeReference, DeclarationsStatus, PreviousDeclarationRequest}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import org.mongodb.scala.Document
import org.scalatest.Inside.inside
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers._
import services.{ChargeReferenceService, ValidationService}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.ObservableFuture

import java.time.{LocalDateTime, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global

class DeclarationsRepositoryISpec
    extends IntegrationSpecCommonBase
    with DefaultPlayMongoRepositorySupport[Declaration] {

  val validationService: ValidationService           = app.injector.instanceOf[ValidationService]
  implicit val mat: Materializer                     = app.injector.instanceOf[Materializer]
  val chargeReferenceService: ChargeReferenceService = app.injector.instanceOf[ChargeReferenceService]
  val configuration: Configuration                   = app.injector.instanceOf[Configuration]

  override val repository =
    new DefaultDeclarationsRepository(mongoComponent, chargeReferenceService, validationService, configuration)

  override def beforeAll(): Unit =
    super.beforeAll()

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.collection.drop().toFuture())
    await(repository.ensureIndexes())
  }

  override def afterEach(): Unit = {
    super.afterEach()
    await(repository.collection.drop().toFuture())
    await(repository.ensureIndexes())
  }

  override def afterAll(): Unit =
    super.afterAll()

  lazy val builder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()

  val userInformation: JsObject = Json.obj(
    "firstName"            -> "Harry",
    "lastName"             -> "Potter",
    "identificationType"   -> "passport",
    "identificationNumber" -> "SX12345",
    "emailAddress"         -> "abc@gmail.com",
    "selectPlaceOfArrival" -> "LHR",
    "enterPlaceOfArrival"  -> "Heathrow Airport",
    "dateOfArrival"        -> "2018-05-31",
    "timeOfArrival"        -> "13:20:00.000"
  )

  val journeyData: JsObject = Json.obj(
    "euCountryCheck"            -> "greatBritain",
    "arrivingNICheck"           -> true,
    "isUKResident"              -> false,
    "bringingOverAllowance"     -> true,
    "privateCraft"              -> true,
    "ageOver17"                 -> true,
    "userInformation"           -> userInformation,
    "amendmentCount"            -> 0,
    "purchasedProductInstances" -> Json.arr(
      Json.obj(
        "path"         -> "other-goods/adult/adult-clothing",
        "iid"          -> "UCLFeP",
        "country"      -> Json.obj(
          "code"            -> "IN",
          "countryName"     -> "title.south_georgia_and_the_south_sandwich_islands",
          "alphaTwoCode"    -> "GS",
          "isEu"            -> false,
          "isCountry"       -> true,
          "countrySynonyms" -> Json.arr()
        ),
        "currency"     -> "GBP",
        "cost"         -> 500,
        "isVatPaid"    -> false,
        "isCustomPaid" -> false,
        "isUccRelief"  -> false
      )
    ),
    "calculatorResponse"        -> Json.obj(
      "calculation" -> Json.obj(
        "excise"  -> "0.00",
        "customs" -> "12.50",
        "vat"     -> "102.50",
        "allTax"  -> "115.00"
      )
    ),
    "deltaCalculation"          -> Some(
      Json.obj("excise" -> "10.00", "customs" -> "10.50", "vat" -> "10.50", "allTax" -> "31.00")
    )
  )

  val inputData: JsObject = Json.obj(
    "journeyData"              -> journeyData,
    "simpleDeclarationRequest" -> Json.obj(
      "requestCommon" -> Json.obj(
        "receiptDate"              -> "2020-12-29T12:14:08Z",
        "requestParameters"        -> Json.arr(
          Json.obj(
            "paramName"  -> "REGIME",
            "paramValue" -> "PNGR"
          )
        ),
        "acknowledgementReference" -> "XMPR00000000000"
      ),
      "requestDetail" -> Json.obj(
        "declarationAlcohol" -> Json.obj(
          "totalExciseAlcohol"     -> "2.00",
          "totalCustomsAlcohol"    -> "0.30",
          "totalVATAlcohol"        -> "18.70",
          "declarationItemAlcohol" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Cider",
              "volume"               -> "5",
              "goodsValue"           -> "120.00",
              "valueCurrency"        -> "USD",
              "valueCurrencyName"    -> "USA dollars (USD)",
              "originCountry"        -> "US",
              "originCountryName"    -> "United States of America",
              "exchangeRate"         -> "1.20",
              "exchangeRateDate"     -> "2018-10-29",
              "goodsValueGBP"        -> "91.23",
              "VATRESClaimed"        -> false,
              "exciseGBP"            -> "2.00",
              "customsGBP"           -> "0.30",
              "vatGBP"               -> "18.70"
            )
          )
        ),
        "liabilityDetails"   -> Json.obj(
          "totalExciseGBP"  -> "102.54",
          "totalCustomsGBP" -> "534.89",
          "totalVATGBP"     -> "725.03",
          "grandTotalGBP"   -> "1362.46"
        ),
        "customerReference"  -> Json.obj("idType" -> "passport", "idValue" -> "SX12345", "ukResident" -> false),
        "personalDetails"    -> Json.obj("firstName" -> "Harry", "lastName" -> "Potter"),
        "declarationTobacco" -> Json.obj(
          "totalExciseTobacco"     -> "100.54",
          "totalCustomsTobacco"    -> "192.94",
          "totalVATTobacco"        -> "149.92",
          "declarationItemTobacco" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Cigarettes",
              "quantity"             -> "250",
              "goodsValue"           -> "400.00",
              "valueCurrency"        -> "USD",
              "valueCurrencyName"    -> "USA dollars (USD)",
              "originCountry"        -> "US",
              "originCountryName"    -> "United States of America",
              "exchangeRate"         -> "1.20",
              "exchangeRateDate"     -> "2018-10-29",
              "goodsValueGBP"        -> "304.11",
              "VATRESClaimed"        -> false,
              "exciseGBP"            -> "74.00",
              "customsGBP"           -> "79.06",
              "vatGBP"               -> "91.43"
            )
          )
        ),
        "declarationHeader"  -> Json.obj(
          "travellingFrom"        -> "NON_EU Only",
          "expectedDateOfArrival" -> "2018-05-31",
          "ukVATPaid"             -> false,
          "uccRelief"             -> false,
          "portOfEntryName"       -> "Heathrow Airport",
          "ukExcisePaid"          -> false,
          "chargeReference"       -> "XMPR0000000000",
          "portOfEntry"           -> "LHR",
          "timeOfEntry"           -> "13:20",
          "onwardTravelGBNI"      -> "GB",
          "messageTypes"          -> Json.obj("messageType" -> "DeclarationCreate")
        ),
        "contactDetails"     -> Json.obj("emailAddress" -> "abc@gmail.com"),
        "declarationOther"   -> Json.obj(
          "totalExciseOther"     -> "0.00",
          "totalCustomsOther"    -> "341.65",
          "totalVATOther"        -> "556.41",
          "declarationItemOther" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Television",
              "quantity"             -> "1",
              "goodsValue"           -> "1500.00",
              "valueCurrency"        -> "USD",
              "valueCurrencyName"    -> "USA dollars (USD)",
              "originCountry"        -> "US",
              "originCountryName"    -> "United States of America",
              "exchangeRate"         -> "1.20",
              "exchangeRateDate"     -> "2018-10-29",
              "goodsValueGBP"        -> "1140.42",
              "VATRESClaimed"        -> false,
              "exciseGBP"            -> "0.00",
              "customsGBP"           -> "159.65",
              "vatGBP"               -> "260.01"
            )
          )
        )
      )
    )
  )

  val actualData: JsObject = Json.obj(
    "simpleDeclarationRequest" -> Json.obj(
      "requestCommon" -> Json.obj(
        "receiptDate"              -> "2020-12-29T12:14:08Z",
        "requestParameters"        -> Json.arr(
          Json.obj(
            "paramName"  -> "REGIME",
            "paramValue" -> "PNGR"
          )
        ),
        "acknowledgementReference" -> "XMPR00000000000"
      ),
      "requestDetail" -> Json.obj(
        "declarationAlcohol" -> Json.obj(
          "totalExciseAlcohol"     -> "2.00",
          "totalCustomsAlcohol"    -> "0.30",
          "totalVATAlcohol"        -> "18.70",
          "declarationItemAlcohol" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Cider",
              "volume"               -> "5",
              "goodsValue"           -> "120.00",
              "valueCurrency"        -> "USD",
              "valueCurrencyName"    -> "USA dollars (USD)",
              "originCountry"        -> "US",
              "originCountryName"    -> "United States of America",
              "exchangeRate"         -> "1.20",
              "exchangeRateDate"     -> "2018-10-29",
              "goodsValueGBP"        -> "91.23",
              "VATRESClaimed"        -> false,
              "exciseGBP"            -> "2.00",
              "customsGBP"           -> "0.30",
              "vatGBP"               -> "18.70"
            )
          )
        ),
        "liabilityDetails"   -> Json.obj(
          "totalExciseGBP"  -> "102.54",
          "totalCustomsGBP" -> "534.89",
          "totalVATGBP"     -> "725.03",
          "grandTotalGBP"   -> "1362.46"
        ),
        "customerReference"  -> Json.obj("idType" -> "passport", "idValue" -> "SX12345", "ukResident" -> false),
        "personalDetails"    -> Json.obj("firstName" -> "Harry", "lastName" -> "Potter"),
        "declarationTobacco" -> Json.obj(
          "totalExciseTobacco"     -> "100.54",
          "totalCustomsTobacco"    -> "192.94",
          "totalVATTobacco"        -> "149.92",
          "declarationItemTobacco" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Cigarettes",
              "quantity"             -> "250",
              "goodsValue"           -> "400.00",
              "valueCurrency"        -> "USD",
              "valueCurrencyName"    -> "USA dollars (USD)",
              "originCountry"        -> "US",
              "originCountryName"    -> "United States of America",
              "exchangeRate"         -> "1.20",
              "exchangeRateDate"     -> "2018-10-29",
              "goodsValueGBP"        -> "304.11",
              "VATRESClaimed"        -> false,
              "exciseGBP"            -> "74.00",
              "customsGBP"           -> "79.06",
              "vatGBP"               -> "91.43"
            )
          )
        ),
        "declarationHeader"  -> Json.obj(
          "travellingFrom"        -> "NON_EU Only",
          "expectedDateOfArrival" -> "2018-05-31",
          "ukVATPaid"             -> false,
          "uccRelief"             -> false,
          "portOfEntryName"       -> "Heathrow Airport",
          "ukExcisePaid"          -> false,
          "chargeReference"       -> "XMPR0000000000",
          "portOfEntry"           -> "LHR",
          "timeOfEntry"           -> "13:20",
          "onwardTravelGBNI"      -> "GB",
          "messageTypes"          -> Json.obj("messageType" -> "DeclarationCreate")
        ),
        "contactDetails"     -> Json.obj("emailAddress" -> "abc@gmail.com"),
        "declarationOther"   -> Json.obj(
          "totalExciseOther"     -> "0.00",
          "totalCustomsOther"    -> "341.65",
          "totalVATOther"        -> "556.41",
          "declarationItemOther" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Television",
              "quantity"             -> "1",
              "goodsValue"           -> "1500.00",
              "valueCurrency"        -> "USD",
              "valueCurrencyName"    -> "USA dollars (USD)",
              "originCountry"        -> "US",
              "originCountryName"    -> "United States of America",
              "exchangeRate"         -> "1.20",
              "exchangeRateDate"     -> "2018-10-29",
              "goodsValueGBP"        -> "1140.42",
              "VATRESClaimed"        -> false,
              "exciseGBP"            -> "0.00",
              "customsGBP"           -> "159.65",
              "vatGBP"               -> "260.01"
            )
          )
        )
      )
    )
  )

  val actualAmendmentData: JsObject = Json.obj(
    "simpleDeclarationRequest" -> Json.obj(
      "requestCommon" -> Json.obj(
        "receiptDate"              -> "2020-12-29T14:00:08Z",
        "requestParameters"        -> Json.arr(
          Json.obj(
            "paramName"  -> "REGIME",
            "paramValue" -> "PNGR"
          )
        ),
        "acknowledgementReference" -> "XMPR00000000001"
      ),
      "requestDetail" -> Json.obj(
        "declarationAlcohol"        -> Json.obj(
          "totalExciseAlcohol"     -> "2.00",
          "totalCustomsAlcohol"    -> "0.30",
          "totalVATAlcohol"        -> "18.70",
          "declarationItemAlcohol" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Cider",
              "volume"               -> "5",
              "goodsValue"           -> "120.00",
              "valueCurrency"        -> "USD",
              "valueCurrencyName"    -> "USA dollars (USD)",
              "originCountry"        -> "US",
              "originCountryName"    -> "United States of America",
              "exchangeRate"         -> "1.20",
              "exchangeRateDate"     -> "2018-10-29",
              "goodsValueGBP"        -> "91.23",
              "VATRESClaimed"        -> false,
              "exciseGBP"            -> "2.00",
              "customsGBP"           -> "0.30",
              "vatGBP"               -> "18.70"
            ),
            Json.obj(
              "commodityDescription" -> "Beer",
              "volume"               -> "110",
              "goodsValue"           -> "500.00",
              "valueCurrency"        -> "GBP",
              "valueCurrencyName"    -> "British pounds (GBP)",
              "originCountry"        -> "FR",
              "originCountryName"    -> "France",
              "exchangeRate"         -> "1.00",
              "exchangeRateDate"     -> "2020-12-29",
              "goodsValueGBP"        -> "500.00",
              "VATRESClaimed"        -> false,
              "exciseGBP"            -> "88.00",
              "customsGBP"           -> "0.00",
              "vatGBP"               -> "117.60"
            )
          )
        ),
        "liabilityDetails"          -> Json.obj(
          "totalExciseGBP"  -> "102.54",
          "totalCustomsGBP" -> "534.89",
          "totalVATGBP"     -> "725.03",
          "grandTotalGBP"   -> "1362.46"
        ),
        "amendmentLiabilityDetails" -> Json.obj(
          "additionalExciseGBP"  -> "88.00",
          "additionalCustomsGBP" -> "0.00",
          "additionalVATGBP"     -> "117.60",
          "additionalTotalGBP"   -> "205.60"
        ),
        "customerReference"         -> Json.obj("idType" -> "passport", "idValue" -> "SX12345", "ukResident" -> false),
        "personalDetails"           -> Json.obj("firstName" -> "Harry", "lastName" -> "Potter"),
        "declarationTobacco"        -> Json.obj(
          "totalExciseTobacco"     -> "100.54",
          "totalCustomsTobacco"    -> "192.94",
          "totalVATTobacco"        -> "149.92",
          "declarationItemTobacco" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Cigarettes",
              "quantity"             -> "250",
              "goodsValue"           -> "400.00",
              "valueCurrency"        -> "USD",
              "valueCurrencyName"    -> "USA dollars (USD)",
              "originCountry"        -> "US",
              "originCountryName"    -> "United States of America",
              "exchangeRate"         -> "1.20",
              "exchangeRateDate"     -> "2018-10-29",
              "goodsValueGBP"        -> "304.11",
              "VATRESClaimed"        -> false,
              "exciseGBP"            -> "74.00",
              "customsGBP"           -> "79.06",
              "vatGBP"               -> "91.43"
            )
          )
        ),
        "declarationHeader"         -> Json.obj(
          "travellingFrom"        -> "NON_EU Only",
          "expectedDateOfArrival" -> "2018-05-31",
          "ukVATPaid"             -> false,
          "uccRelief"             -> false,
          "portOfEntryName"       -> "Heathrow Airport",
          "ukExcisePaid"          -> false,
          "chargeReference"       -> "XMPR0000000000",
          "portOfEntry"           -> "LHR",
          "timeOfEntry"           -> "13:20",
          "onwardTravelGBNI"      -> "GB",
          "messageTypes"          -> Json.obj("messageType" -> "DeclarationAmend")
        ),
        "contactDetails"            -> Json.obj("emailAddress" -> "abc@gmail.com"),
        "declarationOther"          -> Json.obj(
          "totalExciseOther"     -> "0.00",
          "totalCustomsOther"    -> "341.65",
          "totalVATOther"        -> "556.41",
          "declarationItemOther" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Television",
              "quantity"             -> "1",
              "goodsValue"           -> "1500.00",
              "valueCurrency"        -> "USD",
              "valueCurrencyName"    -> "USA dollars (USD)",
              "originCountry"        -> "US",
              "originCountryName"    -> "United States of America",
              "exchangeRate"         -> "1.20",
              "exchangeRateDate"     -> "2018-10-29",
              "goodsValueGBP"        -> "1140.42",
              "VATRESClaimed"        -> false,
              "exciseGBP"            -> "0.00",
              "customsGBP"           -> "159.65",
              "vatGBP"               -> "260.01"
            )
          )
        )
      )
    )
  )

  val inputAmendmentData: JsObject = Json.obj(
    "journeyData"              -> journeyData,
    "simpleDeclarationRequest" -> Json.obj(
      "requestCommon" -> Json.obj(
        "receiptDate"              -> "2020-12-29T14:00:08Z",
        "requestParameters"        -> Json.arr(
          Json.obj(
            "paramName"  -> "REGIME",
            "paramValue" -> "PNGR"
          )
        ),
        "acknowledgementReference" -> "XMPR00000000001"
      ),
      "requestDetail" -> Json.obj(
        "declarationAlcohol"        -> Json.obj(
          "totalExciseAlcohol"     -> "2.00",
          "totalCustomsAlcohol"    -> "0.30",
          "totalVATAlcohol"        -> "18.70",
          "declarationItemAlcohol" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Cider",
              "volume"               -> "5",
              "goodsValue"           -> "120.00",
              "valueCurrency"        -> "USD",
              "valueCurrencyName"    -> "USA dollars (USD)",
              "originCountry"        -> "US",
              "originCountryName"    -> "United States of America",
              "exchangeRate"         -> "1.20",
              "exchangeRateDate"     -> "2018-10-29",
              "goodsValueGBP"        -> "91.23",
              "VATRESClaimed"        -> false,
              "exciseGBP"            -> "2.00",
              "customsGBP"           -> "0.30",
              "vatGBP"               -> "18.70"
            ),
            Json.obj(
              "commodityDescription" -> "Beer",
              "volume"               -> "110",
              "goodsValue"           -> "500.00",
              "valueCurrency"        -> "GBP",
              "valueCurrencyName"    -> "British pounds (GBP)",
              "originCountry"        -> "FR",
              "originCountryName"    -> "France",
              "exchangeRate"         -> "1.00",
              "exchangeRateDate"     -> "2020-12-29",
              "goodsValueGBP"        -> "500.00",
              "VATRESClaimed"        -> false,
              "exciseGBP"            -> "88.00",
              "customsGBP"           -> "0.00",
              "vatGBP"               -> "117.60"
            )
          )
        ),
        "liabilityDetails"          -> Json.obj(
          "totalExciseGBP"  -> "102.54",
          "totalCustomsGBP" -> "534.89",
          "totalVATGBP"     -> "725.03",
          "grandTotalGBP"   -> "1362.46"
        ),
        "amendmentLiabilityDetails" -> Json.obj(
          "additionalExciseGBP"  -> "88.00",
          "additionalCustomsGBP" -> "0.00",
          "additionalVATGBP"     -> "117.60",
          "additionalTotalGBP"   -> "205.60"
        ),
        "customerReference"         -> Json.obj("idType" -> "passport", "idValue" -> "SX12345", "ukResident" -> false),
        "personalDetails"           -> Json.obj("firstName" -> "Harry", "lastName" -> "Potter"),
        "declarationTobacco"        -> Json.obj(
          "totalExciseTobacco"     -> "100.54",
          "totalCustomsTobacco"    -> "192.94",
          "totalVATTobacco"        -> "149.92",
          "declarationItemTobacco" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Cigarettes",
              "quantity"             -> "250",
              "goodsValue"           -> "400.00",
              "valueCurrency"        -> "USD",
              "valueCurrencyName"    -> "USA dollars (USD)",
              "originCountry"        -> "US",
              "originCountryName"    -> "United States of America",
              "exchangeRate"         -> "1.20",
              "exchangeRateDate"     -> "2018-10-29",
              "goodsValueGBP"        -> "304.11",
              "VATRESClaimed"        -> false,
              "exciseGBP"            -> "74.00",
              "customsGBP"           -> "79.06",
              "vatGBP"               -> "91.43"
            )
          )
        ),
        "declarationHeader"         -> Json.obj(
          "travellingFrom"        -> "NON_EU Only",
          "expectedDateOfArrival" -> "2018-05-31",
          "ukVATPaid"             -> false,
          "uccRelief"             -> false,
          "portOfEntryName"       -> "Heathrow Airport",
          "ukExcisePaid"          -> false,
          "chargeReference"       -> "XMPR0000000000",
          "portOfEntry"           -> "LHR",
          "timeOfEntry"           -> "13:20",
          "onwardTravelGBNI"      -> "GB",
          "messageTypes"          -> Json.obj("messageType" -> "DeclarationAmend")
        ),
        "contactDetails"            -> Json.obj("emailAddress" -> "abc@gmail.com"),
        "declarationOther"          -> Json.obj(
          "totalExciseOther"     -> "0.00",
          "totalCustomsOther"    -> "341.65",
          "totalVATOther"        -> "556.41",
          "declarationItemOther" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Television",
              "quantity"             -> "1",
              "goodsValue"           -> "1500.00",
              "valueCurrency"        -> "USD",
              "valueCurrencyName"    -> "USA dollars (USD)",
              "originCountry"        -> "US",
              "originCountryName"    -> "United States of America",
              "exchangeRate"         -> "1.20",
              "exchangeRateDate"     -> "2018-10-29",
              "goodsValueGBP"        -> "1140.42",
              "VATRESClaimed"        -> false,
              "exciseGBP"            -> "0.00",
              "customsGBP"           -> "159.65",
              "vatGBP"               -> "260.01"
            )
          )
        )
      )
    )
  )

  "a declarations repository" should {

    val correlationId      = "fe28db96-d9db-4220-9e12-f2d267267c29"
    val amendCorrelationId = "fe28db96-d9db-4220-9e12-f2d267267c30"

    "must insert and remove declarations" in {
      val app = builder.build()

      running(app) {

        val document = repository
          .asInstanceOf[DeclarationsRepository]
          .insert(inputData, correlationId, sentToEtmp = false)
          .futureValue
          .toOption
          .get

        inside(document) { case Declaration(id, _, None, false, None, cid, None, jd, data, None, _) =>
          id  shouldBe document.chargeReference
          cid shouldBe correlationId
          jd  shouldBe journeyData

          repository.asInstanceOf[DeclarationsRepository].remove(document.chargeReference).futureValue
          repository.asInstanceOf[DeclarationsRepository].get(document.chargeReference).futureValue shouldNot be(
            defined
          )
        }
      }
    }

    "must update a declaration record with amendments and remove record" in {
      val app = builder.build()

      running(app) {

        val declarationDocument =
          repository
            .asInstanceOf[DeclarationsRepository]
            .insert(inputData, correlationId, sentToEtmp = false)
            .futureValue
            .toOption
            .get
        val amendmentDocument   = repository
          .asInstanceOf[DeclarationsRepository]
          .insertAmendment(inputAmendmentData, amendCorrelationId, declarationDocument.chargeReference)
          .futureValue
        journeyData.deepMerge(Json.obj("amendmentCount" -> 1))

        inside(amendmentDocument) {
          case Declaration(
                id,
                _,
                amendState,
                false,
                amendSentToEtmp,
                _,
                correlationIdFromDataBase,
                jd,
                data,
                amendData,
                _
              ) =>
            id                            shouldBe amendmentDocument.chargeReference
            amendState                    shouldBe Some(State.PendingPayment)
            amendSentToEtmp               shouldBe Some(false)
            correlationIdFromDataBase.get shouldBe amendCorrelationId
            jd                            shouldBe journeyData
            amendData                     shouldBe Some(actualAmendmentData)
        }

        repository.asInstanceOf[DeclarationsRepository].remove(amendmentDocument.chargeReference).futureValue
        repository.asInstanceOf[DeclarationsRepository].get(amendmentDocument.chargeReference).futureValue shouldNot be(
          defined
        )
      }
    }

    "must ensure indices" in {
      val app = builder.build()

      running(app) {

        val indices: Seq[Document] = await(repository.collection.listIndexes().toFuture())

        indices.map { doc =>
          doc.toJson() match {
            case json if json.contains("state")           => json.contains("declarations-state-index")      shouldBe true
            case json if json.contains("amendState")      => json.contains("declarations-amendState-index") shouldBe true
            case json if json.contains("sentToEtmp")      => json.contains("declarations-sentToEtmp-index") shouldBe true
            case json if json.contains("amendSentToEtmp") =>
              json.contains("declarations-amendSentToEtmp-index") shouldBe true
            case _                                        => doc.toJson().contains("_id")                   shouldBe true
          }
        }

        indices.size shouldBe 5

      }
    }

    "must provide a stream of unpaid declarations" in {
      val app = builder.configure("declarations.payment-no-response-timeout" -> "1 minute").build()

      running(app) {

        val declarations = List(
          Declaration(
            ChargeReference(0),
            State.PendingPayment,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj(),
            None,
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)
          ),
          Declaration(
            ChargeReference(1),
            State.PaymentFailed,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj(),
            None,
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)
          ),
          Declaration(
            ChargeReference(2),
            State.PaymentCancelled,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj(),
            None,
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)
          ),
          Declaration(
            ChargeReference(3),
            State.Paid,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj(),
            None,
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)
          ),
          Declaration(
            ChargeReference(4),
            State.SubmissionFailed,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj(),
            None,
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)
          ),
          Declaration(
            ChargeReference(5),
            State.PendingPayment,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj(),
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(6),
            State.PendingPayment,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj(),
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          )
        )

        await(repository.collection.insertMany(declarations).toFuture())

        implicit val mat: Materializer = app.injector.instanceOf[Materializer]

        val staleDeclarations =
          repository
            .asInstanceOf[DeclarationsRepository]
            .unpaidDeclarations
            .runWith(Sink.collection[Declaration, List[Declaration]])
            .futureValue

        staleDeclarations.size                 shouldBe 5
        staleDeclarations.map(_.chargeReference) should contain.allOf(
          ChargeReference(0),
          ChargeReference(1),
          ChargeReference(2),
          ChargeReference(5),
          ChargeReference(6)
        )
      }
    }

    "must provide a stream of unpaid amendments" in {
      val app = builder.configure("declarations.payment-no-response-timeout" -> "1 minute").build()

      running(app) {

        val declarations = List(
          Declaration(
            ChargeReference(0),
            State.Paid,
            Some(State.PendingPayment),
            sentToEtmp = true,
            Some(false),
            correlationId,
            Some(amendCorrelationId),
            Json.obj(),
            Json.obj(),
            Some(Json.obj()),
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)
          ),
          Declaration(
            ChargeReference(1),
            State.Paid,
            Some(State.PaymentFailed),
            sentToEtmp = true,
            Some(false),
            correlationId,
            Some(amendCorrelationId),
            Json.obj(),
            Json.obj(),
            Some(Json.obj()),
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)
          ),
          Declaration(
            ChargeReference(2),
            State.Paid,
            Some(State.PaymentCancelled),
            sentToEtmp = true,
            Some(false),
            correlationId,
            Some(amendCorrelationId),
            Json.obj(),
            Json.obj(),
            Some(Json.obj()),
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)
          ),
          Declaration(
            ChargeReference(3),
            State.Paid,
            Some(State.Paid),
            sentToEtmp = true,
            Some(false),
            correlationId,
            Some(amendCorrelationId),
            Json.obj(),
            Json.obj(),
            Some(Json.obj()),
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)
          ),
          Declaration(
            ChargeReference(4),
            State.Paid,
            Some(State.SubmissionFailed),
            sentToEtmp = true,
            Some(false),
            correlationId,
            Some(amendCorrelationId),
            Json.obj(),
            Json.obj(),
            Some(Json.obj()),
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)
          ),
          Declaration(
            ChargeReference(5),
            State.Paid,
            Some(State.PendingPayment),
            sentToEtmp = true,
            Some(false),
            correlationId,
            Some(amendCorrelationId),
            Json.obj(),
            Json.obj(),
            Some(Json.obj()),
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(6),
            State.Paid,
            Some(State.PaymentFailed),
            sentToEtmp = true,
            Some(false),
            correlationId,
            Some(amendCorrelationId),
            Json.obj(),
            Json.obj(),
            Some(Json.obj()),
            LocalDateTime.now(ZoneOffset.UTC)
          )
        )

        await(repository.collection.insertMany(declarations).toFuture())

        val staleDeclarations =
          await(
            repository
              .asInstanceOf[DeclarationsRepository]
              .unpaidAmendments
              .runWith(Sink.collection[Declaration, List[Declaration]])
          )

        staleDeclarations.size                 shouldBe 5
        staleDeclarations.map(_.chargeReference) should contain.allOf(
          ChargeReference(0),
          ChargeReference(1),
          ChargeReference(2),
          ChargeReference(5),
          ChargeReference(6)
        )
      }
    }

    "must set the state of a declaration" in {
      val app = builder.build()

      running(app) {

        val declaration = await(
          repository.asInstanceOf[DeclarationsRepository].insert(inputData, "testId", sentToEtmp = false)
        ).toOption.get

        await(repository.asInstanceOf[DeclarationsRepository].setState(declaration.chargeReference, State.Paid))

        val updatedDeclaration: Declaration =
          repository.asInstanceOf[DeclarationsRepository].get(declaration.chargeReference).futureValue.get

        updatedDeclaration.state shouldBe State.Paid
      }
    }

    "must set the state of an amendment" in {
      val app = builder.build()

      running(app) {

        val declaration = repository
          .asInstanceOf[DeclarationsRepository]
          .insert(inputData, correlationId, sentToEtmp = false)
          .futureValue
          .toOption
          .get
        val amendment   =
          repository
            .asInstanceOf[DeclarationsRepository]
            .insertAmendment(inputAmendmentData, correlationId, declaration.chargeReference)
            .futureValue

        await(repository.asInstanceOf[DeclarationsRepository].setAmendState(amendment.chargeReference, State.Paid))

        val updatedAmendment: Declaration =
          repository.asInstanceOf[DeclarationsRepository].get(declaration.chargeReference).futureValue.get

        updatedAmendment.amendState.get shouldBe State.Paid
      }
    }

    "must provide a stream of paid declarations" in {
      val app = builder.build()

      running(app) {

        val declarations = List(
          Declaration(
            ChargeReference(0),
            State.PendingPayment,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj(),
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(1),
            State.Paid,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj(),
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(2),
            State.SubmissionFailed,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj(),
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(3),
            State.Paid,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj(),
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(4),
            State.Paid,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj(),
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(5),
            State.Paid,
            Some(State.Paid),
            sentToEtmp = false,
            Some(true),
            correlationId,
            None,
            Json.obj(),
            Json.obj(),
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(6),
            State.Paid,
            Some(State.Paid),
            sentToEtmp = false,
            Some(false),
            correlationId,
            None,
            Json.obj(),
            Json.obj(),
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          )
        )

        await(repository.collection.insertMany(declarations).toFuture())

        implicit val mat: Materializer = app.injector.instanceOf[Materializer]

        val paidDeclarations =
          repository
            .asInstanceOf[DeclarationsRepository]
            .paidDeclarationsForEtmp
            .runWith(Sink.collection[Declaration, List[Declaration]])
            .futureValue

        paidDeclarations.map(_.chargeReference) should contain.only(
          ChargeReference(1),
          ChargeReference(3),
          ChargeReference(4),
          ChargeReference(5),
          ChargeReference(6)
        )
      }
    }

    "must provide a stream of paid amendments" in {
      val app = builder.build()

      running(app) {

        val declarations = List(
          Declaration(
            ChargeReference(0),
            State.Paid,
            Some(State.PendingPayment),
            sentToEtmp = false,
            None,
            correlationId,
            Some(amendCorrelationId),
            Json.obj(),
            Json.obj(),
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(1),
            State.Paid,
            Some(State.Paid),
            sentToEtmp = true,
            Some(false),
            correlationId,
            Some(amendCorrelationId),
            Json.obj(),
            Json.obj(),
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(2),
            State.Paid,
            Some(State.SubmissionFailed),
            sentToEtmp = false,
            None,
            correlationId,
            Some(amendCorrelationId),
            Json.obj(),
            Json.obj(),
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(3),
            State.Paid,
            Some(State.Paid),
            sentToEtmp = true,
            Some(false),
            correlationId,
            Some(amendCorrelationId),
            Json.obj(),
            Json.obj(),
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(4),
            State.Paid,
            Some(State.Paid),
            sentToEtmp = false,
            None,
            correlationId,
            Some(amendCorrelationId),
            Json.obj(),
            Json.obj(),
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(5),
            State.Paid,
            Some(State.Paid),
            sentToEtmp = true,
            Some(false),
            correlationId,
            Some(amendCorrelationId),
            Json.obj(),
            Json.obj(),
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(6),
            State.Paid,
            Some(State.Paid),
            sentToEtmp = false,
            Some(false),
            correlationId,
            Some(amendCorrelationId),
            Json.obj(),
            Json.obj(),
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          )
        )

        await(repository.collection.insertMany(declarations).toFuture())

        implicit val mat: Materializer = app.injector.instanceOf[Materializer]

        val paidDeclarations =
          repository
            .asInstanceOf[DeclarationsRepository]
            .paidAmendmentsForEtmp
            .runWith(Sink.collection[Declaration, List[Declaration]])
            .futureValue

        paidDeclarations.map(_.chargeReference) should contain.only(
          ChargeReference(1),
          ChargeReference(3),
          ChargeReference(5)
        )
      }
    }

    "must provide a declaration when a paid declaration or amendment is present for given chargeReference, lastName, identification number" in {
      val app = builder.build()

      val input = PreviousDeclarationRequest("POTTER", ChargeReference(0).toString)

      val resultCalculation =
        Json.obj("excise" -> "0.00", "customs" -> "12.50", "vat" -> "102.50", "allTax" -> "115.00")

      val resultLiabilityDetails = Json.obj(
        "totalExciseGBP"  -> "102.54",
        "totalCustomsGBP" -> "534.89",
        "totalVATGBP"     -> "725.03",
        "grandTotalGBP"   -> "1362.46"
      )

      running(app) {

        val declarations = List(
          Declaration(
            ChargeReference(0),
            State.Paid,
            Some(State.Paid),
            sentToEtmp = false,
            None,
            correlationId,
            None,
            journeyData,
            actualData,
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(1),
            State.Paid,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj(),
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(2),
            State.SubmissionFailed,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj(),
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(3),
            State.Paid,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj(),
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(4),
            State.Paid,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj(),
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          )
        )

        await(repository.collection.insertMany(declarations).toFuture())

        val paidDeclaration = repository.asInstanceOf[DeclarationsRepository].get(input).futureValue

        paidDeclaration.get.isUKResident.get shouldBe false
        paidDeclaration.get.isPrivateTravel  shouldBe true
        paidDeclaration.get.calculation      shouldBe resultCalculation
        paidDeclaration.get.userInformation  shouldBe userInformation
        paidDeclaration.get.liabilityDetails shouldBe resultLiabilityDetails
      }
    }

    "must not provide a declaration when payment failed for declaration or amendment is present for given chargeReference, lastName, identification number" in {
      val app = builder.build()

      val input = PreviousDeclarationRequest("POTTER", ChargeReference(0).toString)

      running(app) {

        val declarations = List(
          Declaration(
            ChargeReference(0),
            State.Paid,
            Some(State.PaymentFailed),
            sentToEtmp = false,
            None,
            correlationId,
            Some(amendCorrelationId),
            journeyData,
            actualData,
            Some(actualAmendmentData),
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(1),
            State.Paid,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            Some(amendCorrelationId),
            Json.obj(),
            Json.obj(),
            Some(actualAmendmentData),
            LocalDateTime.now(ZoneOffset.UTC)
          )
        )

        await(repository.collection.insertMany(declarations).toFuture())

        repository.asInstanceOf[DeclarationsRepository].get(input).futureValue shouldBe None
      }
    }

    "must provide a declaration when paid declaration & pending payment amendment is present for given chargeReference, lastName" in {
      val deltaCalculation =
        Some(Json.obj("excise" -> "10.00", "customs" -> "10.50", "vat" -> "10.50", "allTax" -> "31.00"))

      val app = builder.build()

      val input = PreviousDeclarationRequest("POTTER", ChargeReference(0).toString)

      running(app) {

        val declarations = List(
          Declaration(
            ChargeReference(0),
            State.Paid,
            Some(State.PendingPayment),
            sentToEtmp = false,
            None,
            correlationId,
            Some(amendCorrelationId),
            journeyData,
            actualData,
            Some(actualAmendmentData),
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(1),
            State.Paid,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            Some(amendCorrelationId),
            Json.obj(),
            Json.obj(),
            Some(actualAmendmentData),
            LocalDateTime.now(ZoneOffset.UTC)
          )
        )

        await(repository.collection.insertMany(declarations).toFuture())

        val pendingAmendment = await(repository.asInstanceOf[DeclarationsRepository].get(input))

        pendingAmendment.get.amendState.get   shouldBe "pending-payment"
        pendingAmendment.get.deltaCalculation shouldBe deltaCalculation
      }
    }

    "must provide a stream of submission-failed declarations" in {

      val app = builder.build()

      running(app) {

        val declarations = List(
          Declaration(
            ChargeReference(0),
            State.SubmissionFailed,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj()
          ),
          Declaration(
            ChargeReference(1),
            State.Paid,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj()
          ),
          Declaration(
            ChargeReference(2),
            State.SubmissionFailed,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj()
          ),
          Declaration(
            ChargeReference(3),
            State.PendingPayment,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj()
          )
        )

        await(repository.collection.insertMany(declarations).toFuture())

        implicit val mat: Materializer = app.injector.instanceOf[Materializer]

        val failedDeclarations =
          repository
            .asInstanceOf[DeclarationsRepository]
            .failedDeclarations
            .runWith(Sink.collection[Declaration, List[Declaration]])
            .futureValue

        failedDeclarations.size                 shouldBe 2
        failedDeclarations.map(_.chargeReference) should contain.only(ChargeReference(0), ChargeReference(2))
      }
    }

    "must fail to insert invalid declarations" in {

      val app = builder.build()

      running(app) {

        val errors = repository
          .asInstanceOf[DeclarationsRepository]
          .insert(Json.obj(), correlationId, sentToEtmp = false)
          .futureValue
          .swap
          .toOption
          .get

        errors should contain(
          """required property 'receiptDate' not found""".stripMargin
        )
      }
    }

    "reads the correct number of declaration states" in {
      val app = builder.build()

      running(app) {

        val declarations = List(
          Declaration(
            ChargeReference(0),
            State.SubmissionFailed,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj()
          ),
          Declaration(
            ChargeReference(1),
            State.Paid,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj()
          ),
          Declaration(
            ChargeReference(2),
            State.Paid,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj()
          ),
          Declaration(
            ChargeReference(3),
            State.PendingPayment,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj()
          ),
          Declaration(
            ChargeReference(4),
            State.PendingPayment,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj()
          ),
          Declaration(
            ChargeReference(5),
            State.PendingPayment,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj()
          ),
          Declaration(
            ChargeReference(6),
            State.PaymentCancelled,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj()
          ),
          Declaration(
            ChargeReference(7),
            State.PaymentCancelled,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj()
          ),
          Declaration(
            ChargeReference(8),
            State.PaymentCancelled,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj()
          ),
          Declaration(
            ChargeReference(9),
            State.PaymentCancelled,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj()
          ),
          Declaration(
            ChargeReference(10),
            State.PaymentFailed,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj()
          ),
          Declaration(
            ChargeReference(11),
            State.PaymentFailed,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj()
          ),
          Declaration(
            ChargeReference(12),
            State.PaymentFailed,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj()
          ),
          Declaration(
            ChargeReference(13),
            State.PaymentFailed,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj()
          ),
          Declaration(
            ChargeReference(14),
            State.PaymentFailed,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            Json.obj(),
            Json.obj()
          )
        )

        await(repository.collection.insertMany(declarations).toFuture())

        repository
          .asInstanceOf[DeclarationsRepository]
          .metricsCount
          .runWith(Sink.collection[DeclarationsStatus, List[DeclarationsStatus]])
          .futureValue
          .head shouldBe DeclarationsStatus(
          pendingPaymentCount = 3,
          paymentCompleteCount = 2,
          paymentFailedCount = 5,
          paymentCancelledCount = 4,
          failedSubmissionCount = 1
        )

      }
    }
  }
}
