package uk.gov.pay.connector.it.resources;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.restassured.response.ValidatableResponse;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import javax.ws.rs.core.Response.Status;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.dropwizard.testing.ConfigOverride.config;
import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.OK;
import static org.exparity.hamcrest.date.ZonedDateTimeMatchers.within;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.client.cardid.model.CardInformationFixture.aCardInformation;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.AMOUNT;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_AMOUNT_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_CHARGE_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_DESCRIPTION_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_DESCRIPTION_VALUE;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_MESSAGE_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_REFERENCE_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_REFERENCE_VALUE;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_RETURN_URL_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.RETURN_URL;
import static uk.gov.pay.connector.matcher.ResponseContainsLinkMatcher.containsLink;
import static uk.gov.pay.connector.matcher.ZoneDateTimeAsStringWithinMatcher.isWithin;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.NumberMatcher.isNumber;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.CARD_NUMBER_IN_PAYMENT_LINK_REFERENCE_REJECTED;
import static uk.gov.service.payments.commons.model.Source.CARD_AGENT_INITIATED_MOTO;
import static uk.gov.service.payments.commons.model.Source.CARD_API;
import static uk.gov.service.payments.commons.model.Source.CARD_PAYMENT_LINK;

public class ChargesApiResourceCreateIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension(
            config("eventQueue.eventQueueEnabled", "true"),
            config("captureProcessConfig.backgroundProcessingEnabled", "true")
    );
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("sandbox", app.getLocalPort(), app.getDatabaseTestHelper());
    private static final String VALID_CARD_NUMBER = "4242424242424242";
    private static final String VALID_SERVICE_ID = "valid-service-id";
    private String gatewayAccountId;
    private String liveGatewayAccountId;

    @BeforeEach
    void setup() {
        gatewayAccountId = app.givenSetup()
                .body(toJson(Map.of(
                        "service_id", VALID_SERVICE_ID,
                        "type", GatewayAccountType.TEST,
                        "payment_provider", PaymentGatewayName.SANDBOX.getName(),
                        "service_name", "my-test-service-name"
                )))
                .post("/v1/api/accounts")
                .then()
                .statusCode(201)
                .extract().path("gateway_account_id");
        
        liveGatewayAccountId = app.givenSetup()
                .body(toJson(Map.of(
                        "service_id", VALID_SERVICE_ID,
                        "type", GatewayAccountType.LIVE,
                        "payment_provider", PaymentGatewayName.SANDBOX.getName(),
                        "service_name", "my-test-service-name"
                )))
                .post("/v1/api/accounts")
                .then()
                .statusCode(201)
                .extract().path("gateway_account_id");
        
        app.givenSetup().body(Map.of("op", "replace", "path", "allow_zero_amount", "value", false))
                .patch("/v1/api/accounts/" + liveGatewayAccountId)
                .then()
                .statusCode(200);
    }
    
    @Test
    void testMultipleValidationErrors() {
        app.givenSetup()
                .body(toJson(Map.of(
                        "amount", 0L,
                        "reference", "Test reference",
                        "description", "Test description",
                        "email", "test@example.com",
                        "return_url", "http://service.local/success-page/")
                ))
                .post(format("/v1/api/accounts/%s/charges", liveGatewayAccountId))
                .then().log().body()
                .statusCode(422);
    }
    
    @Nested
    class ByGatewayAccountId {
        
        @Test
        void shouldCreateChargeAndRetrieveDetailsSuccessfully() {
            ValidatableResponse response = app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "Test reference",
                            "description", "Test description",
                            "email", "test@example.com",
                            "return_url", "http://service.local/success-page/",
                            "language", "cy",
                            "metadata", Map.of(),
                            "source", CARD_API)
                    ))
                    .post(format("/v1/api/accounts/%s/charges", gatewayAccountId))
                    .then()
                    .statusCode(Status.CREATED.getStatusCode())
                    .body("charge_id", is(notNullValue()))
                    .body("amount", isNumber(6234L))
                    .body("reference", is("Test reference"))
                    .body("description", is("Test description"))
                    .body("payment_provider", is("sandbox"))
                    .body("return_url", is("http://service.local/success-page/"))
                    .body("email", is("test@example.com"))
                    .body("language", is("cy"))
                    .body("$", not(hasKey("metadata")))
                    .body("delayed_capture", is(false))
                    .body("moto", is(false))
                    .body("state.status", is(CREATED.toExternal().getStatus()))
                    .body("corporate_card_surcharge", is(nullValue()))
                    .body("total_amount", is(nullValue()))
                    .body("containsKey('card_details')", is(false))
                    .body("containsKey('gateway_account')", is(false))
                    .body("refund_summary.amount_submitted", is(0))
                    .body("refund_summary.amount_available", isNumber(6234L))
                    .body("refund_summary.status", is("pending"))
                    .body("settlement_summary.capture_submit_time", nullValue())
                    .body("settlement_summary.captured_time", nullValue())
                    .body("created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(.\\d{1,3})?Z"))
                    .body("created_date", isWithin(10, SECONDS))
                    .body("authorisation_mode", is("web"))
                    .body("containsKey('card_details')", is(false))
                    .contentType(JSON);

            String testChargeId = response.extract().path("charge_id");
            String documentLocation = expectedChargeLocationFor(gatewayAccountId, testChargeId);
            String chargeTokenId = app.getDatabaseTestHelper().getChargeTokenByExternalChargeId(testChargeId);

            response.header("Location", is(documentLocation))
                    .body("links", hasSize(4))
                    .body("links", containsLink("self", "GET", documentLocation))
                    .body("links", containsLink("refunds", "GET", documentLocation + "/refunds"))
                    .body("links", containsLink("next_url", "GET", "http://CardFrontend/secure/" + chargeTokenId))
                    .body("links", containsLink("next_url_post", "POST", "http://CardFrontend/secure",
                            "application/x-www-form-urlencoded", Map.of("chargeTokenId", chargeTokenId)));

            Map<String, Object> charge = app.getDatabaseTestHelper().getChargeByExternalId(testChargeId);
            assertThat(CARD_API.toString(), equalTo(charge.get("source")));

            ValidatableResponse getChargeResponse = app.givenSetup()
                    .get(format("/v1/api/accounts/%s/charges/%s", gatewayAccountId, testChargeId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .contentType(JSON)
                    .body("charge_id", is(notNullValue()))
                    .body("amount", isNumber(6234L))
                    .body("reference", is("Test reference"))
                    .body("description", is("Test description"))
                    .body("payment_provider", is("sandbox"))
                    .body("return_url", is("http://service.local/success-page/"))
                    .body("email", is("test@example.com"))
                    .body("language", is("cy"))
                    .body("$", not(hasKey("metadata")))
                    .body("delayed_capture", is(false))
                    .body("moto", is(false))
                    .body("state.status", is(CREATED.toExternal().getStatus()))
                    .body("corporate_card_surcharge", is(nullValue()))
                    .body("total_amount", is(nullValue()))
                    .body("containsKey('card_details')", is(false))
                    .body("containsKey('gateway_account')", is(false))
                    .body("settlement_summary.capture_submit_time", nullValue())
                    .body("settlement_summary.captured_time", nullValue())
                    .body("refund_summary.amount_submitted", is(0))
                    .body("refund_summary.amount_available", isNumber(AMOUNT))
                    .body("refund_summary.status", is("pending"));

            // Reload the charge token as it should have changed
            String newChargeTokenId = app.getDatabaseTestHelper().getChargeTokenByExternalChargeId(testChargeId);

            getChargeResponse
                    .body("links", hasSize(4))
                    .body("links", containsLink("self", "GET", documentLocation))
                    .body("links", containsLink("refunds", "GET", documentLocation + "/refunds"))
                    .body("links", containsLink("next_url", "GET", "http://CardFrontend/secure/" + newChargeTokenId))
                    .body("links", containsLink("next_url_post", "POST", "http://CardFrontend/secure",
                            "application/x-www-form-urlencoded", Map.of("chargeTokenId", newChargeTokenId)));


            String expectedGatewayAccountCredentialId = app.getDatabaseTestHelper().getGatewayAccountCredentialsForAccount(Long.parseLong(gatewayAccountId)).get(0).get("id").toString();
            String actualGatewayAccountCredentialId = app.getDatabaseTestHelper().getChargeByExternalId(testChargeId).get("gateway_account_credential_id").toString();

            assertThat(actualGatewayAccountCredentialId, is(expectedGatewayAccountCredentialId));
        }

        @Test
        void shouldCreateCharge_withAuthorisationModeMotoApi() {
            app.givenSetup()
                    .body(toJson(Map.of("op", "replace", "path", "allow_authorisation_api", "value", true)))
                    .patch(format("/v1/api/accounts/%s", gatewayAccountId))
                    .then()
                    .statusCode(Status.OK.getStatusCode());

            String testChargeId = app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "Test reference",
                            "description", "Test description",
                            "email", "test@example.com",
                            "authorisation_mode", "moto_api"
                    )))
                    .post(format("/v1/api/accounts/%s/charges", gatewayAccountId))
                    .then()
                    .statusCode(201)
                    .extract().path("charge_id");

            ValidatableResponse getChargeResponse = app.givenSetup()
                    .get(format("/v1/api/accounts/%s/charges/%s", gatewayAccountId, testChargeId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .contentType(JSON)
                    .body("language", is("en"))
                    .body("authorisation_mode", is("moto_api"))
                    .body("return_url", is(nullValue()));

            ArrayList<Map<String, Object>> links = getChargeResponse.extract().body().jsonPath().get("links");
            var authLink = links.stream().filter(link -> link.get("rel").toString().equals("auth_url_post")).findFirst().get();
            assertThat(authLink.get("method").toString(), is("POST"));
            assertThat(authLink.get("type"), is("application/json"));
            var authLinkParams = (Map<String, String>) authLink.get("params");
            assertThat(authLinkParams.get("one_time_token"), is(not(blankOrNullString())));
        }
        
        @Test
        void shouldCreateCharge_withNoEmailField() {
            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "Test reference",
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/"
                    )))
                    .post(format("/v1/api/accounts/%s/charges", gatewayAccountId))
                    .then()
                    .statusCode(Status.CREATED.getStatusCode())
                    .body("charge_id", is(notNullValue()))
                    .body("email", is(nullValue()))
                    .contentType(JSON);
        }

        @Test
        void shouldCreateCharge_whenReferenceIsACardNumber_forAPIPayment() throws JsonProcessingException {
            var cardInformation = aCardInformation().build();
            app.getCardidStub().returnCardInformation(VALID_CARD_NUMBER, cardInformation);

            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", VALID_CARD_NUMBER,
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/",
                            "source", CARD_API
                    )))
                    .post(format("/v1/api/accounts/%s/charges", gatewayAccountId))
                    .then()
                    .statusCode(Status.CREATED.getStatusCode())
                    .body("reference", is(VALID_CARD_NUMBER))
                    .contentType(JSON);
        }

        @Test
        void shouldReturn400_whenReferenceIsACardNumber_forPaymentLinkPayment() throws JsonProcessingException {
            var cardInformation = aCardInformation().build();
            app.getCardidStub().returnCardInformation(VALID_CARD_NUMBER, cardInformation);

            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", VALID_CARD_NUMBER,
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/",
                            "source", CARD_PAYMENT_LINK
                    )))
                    .post(format("/v1/api/accounts/%s/charges", gatewayAccountId))
                    .then()
                    .contentType(JSON)
                    .statusCode(BAD_REQUEST.getStatusCode())
                    .body("error_identifier", is(CARD_NUMBER_IN_PAYMENT_LINK_REFERENCE_REJECTED.toString()))
                    .body("message[0]", is("Card number entered in a payment link reference"));
        }

        @Test
        void shouldCreateCharge_withExternalMetadata() {
            String chargeExternalId = app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "Test reference",
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/",
                            "metadata", Map.of(
                                    "key1", "string",
                                    "key2", true,
                                    "key3", 123,
                                    "key4", 1.23
                            )
                    )))
                    .post(format("/v1/api/accounts/%s/charges", gatewayAccountId))
                    .then()
                    .statusCode(Status.CREATED.getStatusCode())
                    .contentType(JSON)
                    .body("metadata.key1", is("string"))
                    .body("metadata.key2", is(true))
                    .body("metadata.key3", is(123))
                    .body("metadata.key4", is(1.23F))
                    .extract().path("charge_id");

            app.givenSetup()
                    .get(format("/v1/api/accounts/%s/charges/%s", gatewayAccountId, chargeExternalId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("metadata.key1", is("string"))
                    .body("metadata.key2", is(true))
                    .body("metadata.key3", is(123))
                    .body("metadata.key4", is(1.23F));
            
        }

        @Test
        void shouldCreateCharge_withNullMetadata_becauseNullValuesAreNotDeserialised() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("amount", 6234L);
            payload.put("reference", "Test reference");
            payload.put("description", "Test description");
            payload.put("return_url", "http://service.local/success-page/");
            payload.put("metadata", null);

            app.givenSetup()
                    .body(toJson(payload))
                    .post(format("/v1/api/accounts/%s/charges", gatewayAccountId))
                    .then()
                    .statusCode(Status.CREATED.getStatusCode())
                    .body("reference", is("Test reference"));
        }
        
        @Test
        void shouldReturn422_whenMotoIsTrue_ifMotoNotAllowedForAccount() {
            //by default, gateway account does not have moto enabled
            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "Test reference",
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/",
                            "moto", true
                    )))
                    .post(format("/v1/api/accounts/%s/charges", gatewayAccountId))
                    .then()
                    .statusCode(422)
                    .contentType(JSON)
                    .body("message", contains("MOTO payments are not enabled for this gateway account"))
                    .body("error_identifier", is(ErrorIdentifier.MOTO_NOT_ALLOWED.toString()));;
        }

        @Test
        void shouldCreateMotoCharge_whenMotoIsTrue_IfMotoAllowedForAccount() {
            var payload = Map.of(
                    "op", "replace",
                    "path", "allow_moto",
                    "value", true);

            app.givenSetup()
                    .body(toJson(payload))
                    .patch("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .statusCode(OK.getStatusCode());
            
            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "Test reference",
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/",
                            "moto", true
                    )))
                    .post(format("/v1/api/accounts/%s/charges", gatewayAccountId))
                    .then()
                    .statusCode(201)
                    .contentType(JSON)
                    .body("moto", is(true));
        }

        @Test
        void shouldReturn422_whenAmountIsZero_ifAccountDoesNotAllowZeroAmount() {
            //by default, gateway account does not have zero amount enabled
            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 0,
                            "reference", "Test reference",
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/"
                    )))
                    .post(format("/v1/api/accounts/%s/charges", gatewayAccountId))
                    .then()
                    .statusCode(422)
                    .contentType(JSON)
                    .body("message", contains("Zero amount charges are not enabled for this gateway account"))
                    .body("error_identifier", is(ErrorIdentifier.ZERO_AMOUNT_NOT_ALLOWED.toString()));
        }

        @Test
        void shouldCreateCharge_whenAmountIsZero_ifAccountAllowsZeroAmount() {
            var payload = Map.of(
                    "op", "replace",
                    "path", "allow_zero_amount",
                    "value", true);

            app.givenSetup()
                    .body(toJson(payload))
                    .patch("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .statusCode(OK.getStatusCode());
            
            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 0,
                            "reference", "Test reference",
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/"
                    )))
                    .post(format("/v1/api/accounts/%s/charges", gatewayAccountId))
                    .then()
                    .statusCode(201)
                    .contentType(JSON)
                    .body("amount", is(0));
        }
        
        @Test
        void shouldReturn404_whenAccountNotFound() {
            String nonExistentGatewayAccount = "1234123";
            ValidatableResponse response = app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "Test reference",
                            "description", "Test description",
                            "email", "test@example.com",
                            "return_url", "http://service.local/success-page/")))
                    .post(format("/v1/api/accounts/%s/charges", nonExistentGatewayAccount))
                    .then()
                    .statusCode(NOT_FOUND.getStatusCode())
                    .contentType(JSON)
                    .header("Location", is(nullValue()))
                    .body("charge_id", is(nullValue()))
                    .body("message", contains("Unknown gateway account: " + nonExistentGatewayAccount))
                    .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
        }

        @Test
        void shouldReturn403_whenAccountDisabled() {
            app.givenSetup()
                    .body(toJson(Map.of("op", "replace", "path", "disabled", "value", true)))
                    .patch(format("/v1/api/accounts/%s", gatewayAccountId))
                    .then()
                    .statusCode(Status.OK.getStatusCode());

            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "Test reference",
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/"
                    )))
                    .post(format("/v1/api/accounts/%s/charges", gatewayAccountId))
                    .then()
                    .statusCode(403)
                    .contentType(JSON)
                    .body(JSON_MESSAGE_KEY, contains("This gateway account is disabled"));
        }
    }

    @Nested
    class GetChargeByServiceIdAndAccountType {
        
        @Test
        void shouldCreateChargeAndRetrieveDetailsSuccessfully() {
            ValidatableResponse response = app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "Test reference",
                            "description", "Test description",
                            "email", "test@example.com",
                            "return_url", "http://service.local/success-page/",
                            "language", "cy",
                            "metadata", Map.of(),
                            "source", CARD_API)
                    ))
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(Status.CREATED.getStatusCode())
                    .body("charge_id", is(notNullValue()))
                    .body("amount", isNumber(6234L))
                    .body("reference", is("Test reference"))
                    .body("description", is("Test description"))
                    .body("payment_provider", is("sandbox"))
                    .body("return_url", is("http://service.local/success-page/"))
                    .body("email", is("test@example.com"))
                    .body("language", is("cy"))
                    .body("$", not(hasKey("metadata")))
                    .body("delayed_capture", is(false))
                    .body("moto", is(false))
                    .body("state.status", is(CREATED.toExternal().getStatus()))
                    .body("corporate_card_surcharge", is(nullValue()))
                    .body("total_amount", is(nullValue()))
                    .body("containsKey('card_details')", is(false))
                    .body("containsKey('gateway_account')", is(false))
                    .body("refund_summary.amount_submitted", is(0))
                    .body("refund_summary.amount_available", isNumber(6234L))
                    .body("refund_summary.status", is("pending"))
                    .body("settlement_summary.capture_submit_time", nullValue())
                    .body("settlement_summary.captured_time", nullValue())
                    .body("created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(.\\d{1,3})?Z"))
                    .body("created_date", isWithin(10, SECONDS))
                    .body("authorisation_mode", is("web"))
                    .body("containsKey('card_details')", is(false))
                    .contentType(JSON);

            String testChargeId = response.extract().path("charge_id");
            String documentLocation = expectedChargeLocationFor(gatewayAccountId, testChargeId);
            String chargeTokenId = app.getDatabaseTestHelper().getChargeTokenByExternalChargeId(testChargeId);

            response.header("Location", is(documentLocation))
                    .body("links", hasSize(4))
                    .body("links", containsLink("self", "GET", documentLocation))
                    .body("links", containsLink("refunds", "GET", documentLocation + "/refunds"))
                    .body("links", containsLink("next_url", "GET", "http://CardFrontend/secure/" + chargeTokenId))
                    .body("links", containsLink("next_url_post", "POST", "http://CardFrontend/secure",
                            "application/x-www-form-urlencoded", Map.of("chargeTokenId", chargeTokenId)));

            Map<String, Object> charge = app.getDatabaseTestHelper().getChargeByExternalId(testChargeId);
            assertThat(CARD_API.toString(), equalTo(charge.get("source")));
            
            ValidatableResponse getChargeResponse = app.givenSetup()
                    .get(format("/v1/api/service/%s/account/%s/charges/%s", VALID_SERVICE_ID, GatewayAccountType.TEST, testChargeId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .contentType(JSON)
                    .body("charge_id", is(notNullValue()))
                    .body("amount", isNumber(6234L))
                    .body("reference", is("Test reference"))
                    .body("description", is("Test description"))
                    .body("payment_provider", is("sandbox"))
                    .body("return_url", is("http://service.local/success-page/"))
                    .body("email", is("test@example.com"))
                    .body("language", is("cy"))
                    .body("$", not(hasKey("metadata")))
                    .body("delayed_capture", is(false))
                    .body("moto", is(false))
                    .body("state.status", is(CREATED.toExternal().getStatus()))
                    .body("corporate_card_surcharge", is(nullValue()))
                    .body("total_amount", is(nullValue()))
                    .body("containsKey('card_details')", is(false))
                    .body("containsKey('gateway_account')", is(false))
                    .body("settlement_summary.capture_submit_time", nullValue())
                    .body("settlement_summary.captured_time", nullValue())
                    .body("refund_summary.amount_submitted", is(0))
                    .body("refund_summary.amount_available", isNumber(AMOUNT))
                    .body("refund_summary.status", is("pending"));

            // Reload the charge token as it should have changed
            String newChargeTokenId = app.getDatabaseTestHelper().getChargeTokenByExternalChargeId(testChargeId);

            getChargeResponse
                    .body("links", hasSize(4))
                    .body("links", containsLink("self", "GET", documentLocation))
                    .body("links", containsLink("refunds", "GET", documentLocation + "/refunds"))
                    .body("links", containsLink("next_url", "GET", "http://CardFrontend/secure/" + newChargeTokenId))
                    .body("links", containsLink("next_url_post", "POST", "http://CardFrontend/secure",
                            "application/x-www-form-urlencoded", Map.of("chargeTokenId", newChargeTokenId)));


            String expectedGatewayAccountCredentialId = app.getDatabaseTestHelper().getGatewayAccountCredentialsForAccount(Long.parseLong(gatewayAccountId)).get(0).get("id").toString();
            String actualGatewayAccountCredentialId = app.getDatabaseTestHelper().getChargeByExternalId(testChargeId).get("gateway_account_credential_id").toString();

            assertThat(actualGatewayAccountCredentialId, is(expectedGatewayAccountCredentialId));
        }

        @Test
        void shouldCreateCharge_withAuthorisationModeMotoApi() {
            app.givenSetup()
                    .body(toJson(Map.of("op", "replace", "path", "allow_authorisation_api", "value", true)))
                    .patch(format("/v1/api/service/%s/account/%s", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(Status.OK.getStatusCode());

            String testChargeId = app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "Test reference",
                            "description", "Test description",
                            "email", "test@example.com",
                            "authorisation_mode", "moto_api"
                    )))
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(201)
                    .extract().path("charge_id");

            ValidatableResponse getChargeResponse = app.givenSetup()
                    .get(format("/v1/api/service/%s/account/%s/charges/%s", VALID_SERVICE_ID, GatewayAccountType.TEST, testChargeId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .contentType(JSON)
                    .body("authorisation_mode", is("moto_api"))
                    .body("return_url", is(nullValue()));

            ArrayList<Map<String, Object>> links = getChargeResponse.extract().body().jsonPath().get("links");
            var authLink = links.stream().filter(link -> link.get("rel").toString().equals("auth_url_post")).findFirst().get();
            assertThat(authLink.get("method").toString(), is("POST"));
            assertThat(authLink.get("type"), is("application/json"));
            var authLinkParams = (Map<String, String>) authLink.get("params");
            assertThat(authLinkParams.get("one_time_token"), is(not(blankOrNullString())));
        }

        @Test
        void shouldCreateCharge_withNoEmailField() {
            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "Test reference",
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/"
                    )))
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(Status.CREATED.getStatusCode())
                    .body("charge_id", is(notNullValue()))
                    .contentType(JSON);

        }
        
        @Test
        void shouldCreateCharge_whenReferenceIsACardNumber_forAPIPayment() throws JsonProcessingException {
            var cardInformation = aCardInformation().build();
            app.getCardidStub().returnCardInformation(VALID_CARD_NUMBER, cardInformation);

            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", VALID_CARD_NUMBER,
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/",
                            "source", CARD_API
                    )))
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(Status.CREATED.getStatusCode())
                    .body("reference", is(VALID_CARD_NUMBER))
                    .contentType(JSON);
        }

        @Test
        void shouldReturn400_whenReferenceIsACardNumber_forPaymentLinkPayment() throws JsonProcessingException {
            var cardInformation = aCardInformation().build();
            app.getCardidStub().returnCardInformation(VALID_CARD_NUMBER, cardInformation);

            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", VALID_CARD_NUMBER,
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/",
                            "source", CARD_PAYMENT_LINK
                    )))
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .contentType(JSON)
                    .statusCode(BAD_REQUEST.getStatusCode())
                    .body("error_identifier", is(CARD_NUMBER_IN_PAYMENT_LINK_REFERENCE_REJECTED.toString()))
                    .body("message[0]", is("Card number entered in a payment link reference"));
        }

        @Test
        void shouldCreateCharge_withExternalMetadata() {
            String chargeExternalId = app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "Test reference",
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/",
                            "metadata", Map.of(
                                    "key1", "string",
                                    "key2", true,
                                    "key3", 123,
                                    "key4", 1.23
                            )
                    )))
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(Status.CREATED.getStatusCode())
                    .contentType(JSON)
                    .body("metadata.key1", is("string"))
                    .body("metadata.key2", is(true))
                    .body("metadata.key3", is(123))
                    .body("metadata.key4", is(1.23F))
                    .extract().path("charge_id");

            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/%s/charges/%s", VALID_SERVICE_ID, GatewayAccountType.TEST, chargeExternalId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("metadata.key1", is("string"))
                    .body("metadata.key2", is(true))
                    .body("metadata.key3", is(123))
                    .body("metadata.key4", is(1.23F));
        }

        @Test
        void shouldCreateCharge_whenMetadataIsNull_becauseNullValuesAreNotDeserialised() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("amount", 6234L);
            payload.put("reference", "Test reference");
            payload.put("description", "Test description");
            payload.put("return_url", "http://service.local/success-page/");
            payload.put("metadata", null);

            app.givenSetup()
                    .body(toJson(payload))
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(Status.CREATED.getStatusCode())
                    .body("reference", is("Test reference"));
        }

        @Test
        void shouldReturn422_whenMotoIsTrue_ifMotoNotAllowedForAccount() {
            //by default, gateway account does not have moto enabled
            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "Test reference",
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/",
                            "moto", true
                    )))
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(422)
                    .contentType(JSON)
                    .body("message", contains("MOTO payments are not enabled for this gateway account"))
                    .body("error_identifier", is(ErrorIdentifier.MOTO_NOT_ALLOWED.toString()));;
        }

        @Test
        void shouldCreateMotoCharge_whenMotoIsTrue_IfMotoAllowedForAccount() {
            var payload = Map.of(
                    "op", "replace",
                    "path", "allow_moto",
                    "value", true);

            app.givenSetup()
                    .body(toJson(payload))
                    .patch(format("/v1/api/service/%s/account/%s/", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(OK.getStatusCode());

            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "Test reference",
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/",
                            "moto", true
                    )))
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(201)
                    .contentType(JSON)
                    .body("moto", is(true));
        }

        @Test
        void shouldReturn422_whenAmountIsZero_ifAccountDoesNotAllowZeroAmount() {
            //by default, gateway account does not have zero amount enabled
            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 0,
                            "reference", "Test reference",
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/"
                    )))
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(422)
                    .contentType(JSON)
                    .body("message", contains("Zero amount charges are not enabled for this gateway account"))
                    .body("error_identifier", is(ErrorIdentifier.ZERO_AMOUNT_NOT_ALLOWED.toString()));
        }

        @Test
        void shouldCreateCharge_whenAmountIsZero_ifAccountAllowsZeroAmount() {
            var payload = Map.of(
                    "op", "replace",
                    "path", "allow_zero_amount",
                    "value", true);

            app.givenSetup()
                    .body(toJson(payload)) 
                    .patch(format("/v1/api/service/%s/account/%s/", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(OK.getStatusCode());

            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 0,
                            "reference", "Test reference",
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/"
                    )))
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(201)
                    .contentType(JSON)
                    .body("amount", is(0));
        }
        
        @Test
        void shouldReturn404_whenServiceIdDoesNotExist() {
            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "4242424242424242",
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/"
                    )))
                    .post(format("/v1/api/service/%s/account/%s/charges", "non-existent-service-id", GatewayAccountType.TEST))
                    .then()
                    .statusCode(NOT_FOUND.getStatusCode())
                    .body("message[0]", CoreMatchers.is("Gateway account not found for service ID [non-existent-service-id] and account type [test]"));
        }

        @Test
        void shouldReturn404_whenAccountNotFound() {
            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "4242424242424242",
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/"
                    )))
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.LIVE))
                    .then()
                    .statusCode(NOT_FOUND.getStatusCode())
                    .body("message[0]", CoreMatchers.is("Gateway account not found for service ID [valid-service-id] and account type [live]"));;
        }
        
        @Test
        void shouldReturn403_whenAccountDisabled() {
            app.givenSetup()
                    .body(toJson(Map.of("op", "replace", "path", "disabled", "value", true)))
                    .patch(format("/v1/api/service/%s/account/%s", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(Status.OK.getStatusCode());
            
            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "Test reference",
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/"
                    )))
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(403)
                    .contentType(JSON)
                    .body("message", contains("This gateway account is disabled"));
        }
    }
        /*
    This test breaks when the device running the test is on BST (UTC+1). This is because JDBI assumes
    the time stored in the database (UTC) is in local time (BST) and incorrectly tries to "correct" it to UTC
    by moving it back an hour which results in the assertion failing as it is now 1 hour apart.
     */
    @Disabled("British Summer Time cause this test to fail")
    @Test
    void shouldEmitPaymentCreatedEventWhenChargeIsSuccessfullyCreated() throws Exception {
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL
        ));

        final ValidatableResponse response = testBaseExtension.getConnectorRestApiClient()
                .postCreateCharge(postBody)
                .statusCode(201);

        String chargeExternalId = response.extract().path(JSON_CHARGE_KEY);
        final Map<String, Object> persistedCharge = app.getDatabaseTestHelper().getChargeByExternalId(chargeExternalId);
        final ZonedDateTime persistedCreatedDate = ZonedDateTime.ofInstant(((Timestamp) persistedCharge.get("created_date")).toInstant(), ZoneOffset.UTC);

        Thread.sleep(100);
        List<Message> messages = readMessagesFromEventQueue();

        final Message message = messages.get(0);
        ZonedDateTime eventTimestamp = ZonedDateTime.parse(
                new JsonParser()
                        .parse(message.getBody())
                        .getAsJsonObject()
                        .get("timestamp")
                        .getAsString()
        );

        Optional<JsonObject> createdMessage = messages.stream()
                .map(m -> new JsonParser().parse(m.getBody()).getAsJsonObject())
                .filter(e -> e.get("event_type").getAsString().equals("PAYMENT_CREATED"))
                .findFirst();
        assertThat(createdMessage.isPresent(), is(true));
        assertThat(eventTimestamp, is(within(200, MILLIS, persistedCreatedDate)));
    }

    private List<Message> readMessagesFromEventQueue() {
        AmazonSQS sqsClient = app.getInstanceFromGuiceContainer(AmazonSQS.class);

        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(app.getEventQueueUrl());
        receiveMessageRequest
                .withMessageAttributeNames()
                .withWaitTimeSeconds(1)
                .withMaxNumberOfMessages(10);

        ReceiveMessageResult receiveMessageResult = sqsClient.receiveMessage(receiveMessageRequest);

        return receiveMessageResult.getMessages();
    }

    private String expectedChargeLocationFor(String accountId, String chargeId) {
        return "https://localhost:" + app.getLocalPort() + "/v1/api/accounts/{accountId}/charges/{chargeId}"
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId);
    }

}
