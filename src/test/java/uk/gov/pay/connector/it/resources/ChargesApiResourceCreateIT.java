package uk.gov.pay.connector.it.resources;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.PurgeQueueRequest;
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

import javax.ws.rs.core.Response;
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
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.eclipse.jetty.http.HttpStatus.FORBIDDEN_403;
import static org.exparity.hamcrest.date.ZonedDateTimeMatchers.within;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.client.cardid.model.CardInformationFixture.aCardInformation;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_CREATED;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.AMOUNT;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.EMAIL;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_AMOUNT_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_AUTH_MODE_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_AUTH_MODE_MOTO_API;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_CHARGE_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_DESCRIPTION_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_DESCRIPTION_VALUE;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_EMAIL_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_LANGUAGE_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_MESSAGE_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_MOTO_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_PROVIDER_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_REFERENCE_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_REFERENCE_VALUE;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_RETURN_URL_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_SOURCE_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.PROVIDER_NAME;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.RETURN_URL;
import static uk.gov.pay.connector.matcher.ResponseContainsLinkMatcher.containsLink;
import static uk.gov.pay.connector.matcher.ZoneDateTimeAsStringWithinMatcher.isWithin;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.NumberMatcher.isNumber;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.CARD_NUMBER_IN_PAYMENT_LINK_REFERENCE_REJECTED;
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


    private static final String FRONTEND_CARD_DETAILS_URL = "/secure";
    private static final String JSON_STATE_KEY = "state.status";
    private static final String JSON_DELAYED_CAPTURE_KEY = "delayed_capture";
    private static final String JSON_CORPORATE_CARD_SURCHARGE_KEY = "corporate_card_surcharge";
    private static final String JSON_TOTAL_AMOUNT_KEY = "total_amount";
    private static final String VALID_CARD_NUMBER = "4242424242424242";

    
    
    @Nested
    class ByGatewayAccountId {
        @Test
        void makeChargeAndRetrieveAmount() {
            String postBody = toJson(Map.of(
                    JSON_AMOUNT_KEY, AMOUNT,
                    JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                    JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                    JSON_RETURN_URL_KEY, RETURN_URL,
                    JSON_EMAIL_KEY, EMAIL,
                    JSON_LANGUAGE_KEY, "cy"
            ));

            ValidatableResponse response = testBaseExtension.getConnectorRestApiClient()
                    .postCreateCharge(postBody)
                    .statusCode(Status.CREATED.getStatusCode())
                    .body(JSON_CHARGE_KEY, is(notNullValue()))
                    .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                    .body(JSON_REFERENCE_KEY, is(JSON_REFERENCE_VALUE))
                    .body(JSON_DESCRIPTION_KEY, is(JSON_DESCRIPTION_VALUE))
                    .body(JSON_PROVIDER_KEY, is(PROVIDER_NAME))
                    .body(JSON_RETURN_URL_KEY, is(RETURN_URL))
                    .body(JSON_EMAIL_KEY, is(EMAIL))
                    .body(JSON_LANGUAGE_KEY, is("cy"))
                    .body(JSON_DELAYED_CAPTURE_KEY, is(false))
                    .body(JSON_MOTO_KEY, is(false))
                    .body("containsKey('card_details')", is(false))
                    .body("containsKey('gateway_account')", is(false))
                    .body("refund_summary.amount_submitted", is(0))
                    .body("refund_summary.amount_available", isNumber(AMOUNT))
                    .body("refund_summary.status", is("pending"))
                    .body("settlement_summary.capture_submit_time", nullValue())
                    .body("settlement_summary.captured_time", nullValue())
                    .body("created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(.\\d{1,3})?Z"))
                    .body("created_date", isWithin(10, SECONDS))
                    .body("authorisation_mode", is("web"))
                    .contentType(JSON);

            String externalChargeId = response.extract().path(JSON_CHARGE_KEY);
            String documentLocation = expectedChargeLocationFor(testBaseExtension.getAccountId(), externalChargeId);
            String chargeTokenId = app.getDatabaseTestHelper().getChargeTokenByExternalChargeId(externalChargeId);

            String hrefNextUrl = "http://CardFrontend" + FRONTEND_CARD_DETAILS_URL + "/" + chargeTokenId;
            String hrefNextUrlPost = "http://CardFrontend" + FRONTEND_CARD_DETAILS_URL;

            response.header("Location", is(documentLocation))
                    .body("links", hasSize(4))
                    .body("links", containsLink("self", "GET", documentLocation))
                    .body("links", containsLink("refunds", "GET", documentLocation + "/refunds"))
                    .body("links", containsLink("next_url", "GET", hrefNextUrl))
                    .body("links", containsLink("next_url_post", "POST", hrefNextUrlPost, "application/x-www-form-urlencoded", new HashMap<>() {{
                        put("chargeTokenId", chargeTokenId);
                    }}));

            ValidatableResponse getChargeResponse = testBaseExtension.getConnectorRestApiClient()
                    .withAccountId(testBaseExtension.getAccountId())
                    .withChargeId(externalChargeId)
                    .getCharge()
                    .statusCode(OK.getStatusCode())
                    .contentType(JSON)
                    .body(JSON_CHARGE_KEY, is(externalChargeId))
                    .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                    .body(JSON_REFERENCE_KEY, is(JSON_REFERENCE_VALUE))
                    .body(JSON_DESCRIPTION_KEY, is(JSON_DESCRIPTION_VALUE))
                    .body(JSON_STATE_KEY, is(CREATED.toExternal().getStatus()))
                    .body(JSON_RETURN_URL_KEY, is(RETURN_URL))
                    .body(JSON_EMAIL_KEY, is(EMAIL))
                    .body(JSON_LANGUAGE_KEY, is("cy"))
                    .body(JSON_DELAYED_CAPTURE_KEY, is(false))
                    .body(JSON_CORPORATE_CARD_SURCHARGE_KEY, is(nullValue()))
                    .body(JSON_TOTAL_AMOUNT_KEY, is(nullValue()))
                    .body(JSON_MOTO_KEY, is(false))
                    .body("containsKey('card_details')", is(false))
                    .body("containsKey('gateway_account')", is(false))
                    .body("settlement_summary.capture_submit_time", nullValue())
                    .body("settlement_summary.captured_time", nullValue())
                    .body("refund_summary.amount_submitted", is(0))
                    .body("refund_summary.amount_available", isNumber(AMOUNT))
                    .body("refund_summary.status", is("pending"));


            // Reload the charge token which as it should have changed
            String newChargeTokenId = app.getDatabaseTestHelper().getChargeTokenByExternalChargeId(externalChargeId);

            String newHrefNextUrl = "http://CardFrontend" + FRONTEND_CARD_DETAILS_URL + "/" + newChargeTokenId;

            getChargeResponse
                    .body("links", hasSize(4))
                    .body("links", containsLink("self", "GET", documentLocation))
                    .body("links", containsLink("refunds", "GET", documentLocation + "/refunds"))
                    .body("links", containsLink("next_url", "GET", newHrefNextUrl))
                    .body("links", containsLink("next_url_post", "POST", hrefNextUrlPost, "application/x-www-form-urlencoded", new HashMap<>() {{
                        put("chargeTokenId", newChargeTokenId);
                    }}));

            String expectedGatewayAccountCredentialId = app.getDatabaseTestHelper().getGatewayAccountCredentialsForAccount(testBaseExtension.getTestAccount().getAccountId()).get(0).get("id").toString();
            String actualGatewayAccountCredentialId = app.getDatabaseTestHelper().getChargeByExternalId(externalChargeId).get("gateway_account_credential_id").toString();

            assertThat(actualGatewayAccountCredentialId, is(expectedGatewayAccountCredentialId));
        }

        @Test
        void makeChargeWithAuthorisationModeMotoApi() {
            String postBody = toJson(Map.of(
                    JSON_AMOUNT_KEY, AMOUNT,
                    JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                    JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                    JSON_EMAIL_KEY, EMAIL,
                    JSON_AUTH_MODE_KEY, JSON_AUTH_MODE_MOTO_API
            ));

            ValidatableResponse createResponse = testBaseExtension.getConnectorRestApiClient()
                    .postCreateCharge(postBody)
                    .statusCode(Status.CREATED.getStatusCode())
                    .body(JSON_LANGUAGE_KEY, is("en"))
                    .contentType(JSON);

            String externalChargeId = createResponse.extract().path(JSON_CHARGE_KEY);

            ValidatableResponse findResponse = testBaseExtension.getConnectorRestApiClient()
                    .withAccountId(testBaseExtension.getAccountId())
                    .withChargeId(externalChargeId)
                    .getCharge()
                    .statusCode(OK.getStatusCode())
                    .contentType(JSON)
                    .body(JSON_AUTH_MODE_KEY, is(JSON_AUTH_MODE_MOTO_API))
                    .body(JSON_RETURN_URL_KEY, is(nullValue()));

            ArrayList<Map<String, Object>> links = findResponse.extract().body().jsonPath().get("links");
            var authLink = links.stream().filter(link -> link.get("rel").toString().equals("auth_url_post")).findFirst().get();
            assertThat(authLink.get("method").toString(), is("POST"));
            assertThat(authLink.get("type"), is("application/json"));
            var authLinkParams = (Map<String, String>) authLink.get("params");
            assertThat(authLinkParams.get("one_time_token"), is(not(blankOrNullString())));
        }

        @Test
        void makeChargeNoEmailField_shouldReturnOK() {
            String postBody = toJson(Map.of(
                    JSON_AMOUNT_KEY, AMOUNT,
                    JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                    JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                    JSON_RETURN_URL_KEY, RETURN_URL
            ));


            testBaseExtension.getConnectorRestApiClient()
                    .postCreateCharge(postBody)
                    .statusCode(Status.CREATED.getStatusCode())
                    .body(JSON_CHARGE_KEY, is(notNullValue()))
                    .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                    .body(JSON_REFERENCE_KEY, is(JSON_REFERENCE_VALUE))
                    .body(JSON_DESCRIPTION_KEY, is(JSON_DESCRIPTION_VALUE))
                    .body(JSON_PROVIDER_KEY, is(PROVIDER_NAME))
                    .body(JSON_RETURN_URL_KEY, is(RETURN_URL))
                    .body("containsKey('card_details')", is(false))
                    .body("containsKey('gateway_account')", is(false))
                    .body("created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(.\\d{1,3})?Z"))
                    .body("created_date", isWithin(10, SECONDS))
                    .contentType(JSON);

        }

        @Test
        void shouldCreateCharge_whenReferenceIsACardNumberForAPIPayment() throws JsonProcessingException {
            var cardInformation = aCardInformation().build();
            app.getCardidStub().returnCardInformation(VALID_CARD_NUMBER, cardInformation);

            String postBody = toJson(Map.of(
                    JSON_AMOUNT_KEY, AMOUNT,
                    JSON_REFERENCE_KEY, VALID_CARD_NUMBER,
                    JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                    JSON_RETURN_URL_KEY, RETURN_URL,
                    JSON_SOURCE_KEY, CARD_API
            ));

            testBaseExtension.getConnectorRestApiClient()
                    .postCreateCharge(postBody)
                    .statusCode(Status.CREATED.getStatusCode())
                    .body(JSON_REFERENCE_KEY, is(VALID_CARD_NUMBER))
                    .contentType(JSON);
        }

        @Test
        void shouldReturn404WhenCreatingChargeAccountIdIsNonNumeric() {
            String postBody = toJson(Map.of(
                    JSON_AMOUNT_KEY, AMOUNT,
                    JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                    JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                    JSON_RETURN_URL_KEY, RETURN_URL
            ));

            testBaseExtension.getConnectorRestApiClient()
                    .withAccountId("invalidAccountId")
                    .postCreateCharge(postBody)
                    .contentType(JSON)
                    .statusCode(NOT_FOUND.getStatusCode())
                    .body("code", is(404))
                    .body("message", is("HTTP 404 Not Found"));
        }

        @Test
        void shouldReturn400WhenReferenceIsACardNumberForPaymentLinkPayment() throws JsonProcessingException {
            var cardInformation = aCardInformation().build();
            app.getCardidStub().returnCardInformation(VALID_CARD_NUMBER, cardInformation);

            String postBody = toJson(Map.of(
                    JSON_AMOUNT_KEY, AMOUNT,
                    JSON_REFERENCE_KEY, VALID_CARD_NUMBER,
                    JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                    JSON_RETURN_URL_KEY, RETURN_URL,
                    JSON_SOURCE_KEY, CARD_PAYMENT_LINK
            ));

            testBaseExtension.getConnectorRestApiClient()
                    .withAccountId(testBaseExtension.getAccountId())
                    .postCreateCharge(postBody)
                    .contentType(JSON)
                    .statusCode(BAD_REQUEST.getStatusCode())
                    .body("error_identifier", is(CARD_NUMBER_IN_PAYMENT_LINK_REFERENCE_REJECTED.toString()))
                    .body("message[0]", is("Card number entered in a payment link reference"));
        }

        @Test
        void cannotMakeChargeForMissingGatewayAccount() {
            String missingGatewayAccount = "1234123";
            String postBody = toJson(Map.of(
                    JSON_AMOUNT_KEY, AMOUNT,
                    JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                    JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                    JSON_EMAIL_KEY, EMAIL,
                    JSON_RETURN_URL_KEY, RETURN_URL
            ));

            testBaseExtension.getConnectorRestApiClient()
                    .withAccountId(missingGatewayAccount)
                    .postCreateCharge(postBody)
                    .statusCode(NOT_FOUND.getStatusCode())
                    .contentType(JSON)
                    .header("Location", is(nullValue()))
                    .body(JSON_CHARGE_KEY, is(nullValue()))
                    .body(JSON_MESSAGE_KEY, contains("Unknown gateway account: " + missingGatewayAccount))
                    .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
        }

        @Test
        void cannotMakeChargeForInvalidSizeOfFields() {
            String postBody = toJson(Map.of(
                    JSON_AMOUNT_KEY, AMOUNT,
                    JSON_REFERENCE_KEY, randomAlphabetic(256),
                    JSON_DESCRIPTION_KEY, randomAlphanumeric(256),
                    JSON_EMAIL_KEY, randomAlphanumeric(255),
                    JSON_RETURN_URL_KEY, RETURN_URL
            ));

            testBaseExtension.getConnectorRestApiClient().postCreateCharge(postBody)
                    .statusCode(422)
                    .contentType(JSON)
                    .header("Location", is(nullValue()))
                    .body(JSON_CHARGE_KEY, is(nullValue()))
                    .body(JSON_MESSAGE_KEY, containsInAnyOrder(
                            "Field [email] can have a size between 0 and 254",
                            "Field [description] can have a size between 0 and 255",
                            "Field [reference] can have a size between 0 and 255"
                    ));
        }

        @Test
        void cannotMakeChargeForMissingFields() {
            testBaseExtension.getConnectorRestApiClient().postCreateCharge("{}")
                    .statusCode(422)
                    .contentType(JSON)
                    .header("Location", is(nullValue()))
                    .body(JSON_CHARGE_KEY, is(nullValue()))
                    .body(JSON_MESSAGE_KEY, containsInAnyOrder(
                            "Field [reference] cannot be null",
                            "Field [description] cannot be null",
                            "Field [amount] cannot be null"
                    ));
        }

        @Test
        void shouldReturn403WhenGatewayAccountIsDisabled() {
            app.getDatabaseTestHelper().setDisabled(Long.parseLong(testBaseExtension.getAccountId()));

            String postBody = toJson(Map.of(
                    JSON_AMOUNT_KEY, AMOUNT,
                    JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                    JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                    JSON_RETURN_URL_KEY, RETURN_URL
            ));

            testBaseExtension.getConnectorRestApiClient()
                    .postCreateCharge(postBody)
                    .statusCode(FORBIDDEN_403)
                    .contentType(JSON)
                    .body("message", contains("This gateway account is disabled"))
                    .body("error_identifier", is(ErrorIdentifier.ACCOUNT_DISABLED.toString()));
        }
    }

    @Nested
    class GetChargeByServiceIdAndAccountType {

        private static String VALID_SERVICE_ID = "valid-service-id";
        private static String NON_EXISTENT_SERVICE_ID = "non-existent-service-id";

        private String gatewayAccountId;
        
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
        }
        
        @Test
        void shouldCreateChargeAndRetrieveDetailsSuccessfully() {
            ValidatableResponse response = app.givenSetup()
                    .body(toJson(Map.of(
                            JSON_AMOUNT_KEY, AMOUNT,
                            JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                            JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                            JSON_RETURN_URL_KEY, RETURN_URL,
                            JSON_EMAIL_KEY, EMAIL,
                            JSON_LANGUAGE_KEY, "cy")
                    ))
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(Status.CREATED.getStatusCode())
                    .body(JSON_CHARGE_KEY, is(notNullValue()))
                    .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                    .body(JSON_REFERENCE_KEY, is(JSON_REFERENCE_VALUE))
                    .body(JSON_DESCRIPTION_KEY, is(JSON_DESCRIPTION_VALUE))
                    .body(JSON_PROVIDER_KEY, is(PROVIDER_NAME))
                    .body(JSON_RETURN_URL_KEY, is(RETURN_URL))
                    .body(JSON_EMAIL_KEY, is(EMAIL))
                    .body(JSON_LANGUAGE_KEY, is("cy"))
                    .body(JSON_DELAYED_CAPTURE_KEY, is(false))
                    .body(JSON_MOTO_KEY, is(false))
                    .body("containsKey('card_details')", is(false))
                    .body("containsKey('gateway_account')", is(false))
                    .body("refund_summary.amount_submitted", is(0))
                    .body("refund_summary.amount_available", isNumber(AMOUNT))
                    .body("refund_summary.status", is("pending"))
                    .body("settlement_summary.capture_submit_time", nullValue())
                    .body("settlement_summary.captured_time", nullValue())
                    .body("created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(.\\d{1,3})?Z"))
                    .body("created_date", isWithin(10, SECONDS))
                    .body("authorisation_mode", is("web"))
                    .contentType(JSON);

            String testChargeId = response.extract().path(JSON_CHARGE_KEY);
            String documentLocation = expectedChargeLocationFor(gatewayAccountId, testChargeId);
            String chargeTokenId = app.getDatabaseTestHelper().getChargeTokenByExternalChargeId(testChargeId);
            String hrefNextUrl = "http://CardFrontend" + FRONTEND_CARD_DETAILS_URL + "/" + chargeTokenId;
            String hrefNextUrlPost = "http://CardFrontend" + FRONTEND_CARD_DETAILS_URL;

            response.header("Location", is(documentLocation))
                    .body("links", hasSize(4))
                    .body("links", containsLink("self", "GET", documentLocation))
                    .body("links", containsLink("refunds", "GET", documentLocation + "/refunds"))
                    .body("links", containsLink("next_url", "GET", hrefNextUrl))
                    .body("links", containsLink("next_url_post", "POST", hrefNextUrlPost, "application/x-www-form-urlencoded", new HashMap<>() {{
                        put("chargeTokenId", chargeTokenId);
                    }}));

            ValidatableResponse getChargeResponse = app.givenSetup()
                    .get(format("/v1/api/service/%s/account/%s/charges/%s", VALID_SERVICE_ID, GatewayAccountType.TEST, testChargeId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .contentType(JSON)
                    .body(JSON_CHARGE_KEY, is(testChargeId))
                    .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                    .body(JSON_REFERENCE_KEY, is(JSON_REFERENCE_VALUE))
                    .body(JSON_DESCRIPTION_KEY, is(JSON_DESCRIPTION_VALUE))
                    .body(JSON_STATE_KEY, is(CREATED.toExternal().getStatus()))
                    .body(JSON_RETURN_URL_KEY, is(RETURN_URL))
                    .body(JSON_EMAIL_KEY, is(EMAIL))
                    .body(JSON_LANGUAGE_KEY, is("cy"))
                    .body(JSON_DELAYED_CAPTURE_KEY, is(false))
                    .body(JSON_CORPORATE_CARD_SURCHARGE_KEY, is(nullValue()))
                    .body(JSON_TOTAL_AMOUNT_KEY, is(nullValue()))
                    .body(JSON_MOTO_KEY, is(false))
                    .body("containsKey('card_details')", is(false))
                    .body("containsKey('gateway_account')", is(false))
                    .body("settlement_summary.capture_submit_time", nullValue())
                    .body("settlement_summary.captured_time", nullValue())
                    .body("refund_summary.amount_submitted", is(0))
                    .body("refund_summary.amount_available", isNumber(AMOUNT))
                    .body("refund_summary.status", is("pending"));


            // Reload the charge token as it should have changed
            String newChargeTokenId = app.getDatabaseTestHelper().getChargeTokenByExternalChargeId(testChargeId);
            String newHrefNextUrl = "http://CardFrontend" + FRONTEND_CARD_DETAILS_URL + "/" + newChargeTokenId;

            getChargeResponse
                    .body("links", hasSize(4))
                    .body("links", containsLink("self", "GET", documentLocation))
                    .body("links", containsLink("refunds", "GET", documentLocation + "/refunds"))
                    .body("links", containsLink("next_url", "GET", newHrefNextUrl))
                    .body("links", containsLink("next_url_post", "POST", hrefNextUrlPost, "application/x-www-form-urlencoded", new HashMap<>() {{
                        put("chargeTokenId", newChargeTokenId);
                    }}));

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
                            JSON_AMOUNT_KEY, AMOUNT,
                            JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                            JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                            JSON_EMAIL_KEY, EMAIL,
                            JSON_AUTH_MODE_KEY, JSON_AUTH_MODE_MOTO_API
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
                    .body(JSON_AUTH_MODE_KEY, is(JSON_AUTH_MODE_MOTO_API))
                    .body(JSON_RETURN_URL_KEY, is(nullValue()));

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
                            JSON_AMOUNT_KEY, AMOUNT,
                            JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                            JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                            JSON_RETURN_URL_KEY, RETURN_URL
                    )))
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(Status.CREATED.getStatusCode())
                    .body(JSON_CHARGE_KEY, is(notNullValue()))
                    .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                    .body(JSON_REFERENCE_KEY, is(JSON_REFERENCE_VALUE))
                    .body(JSON_DESCRIPTION_KEY, is(JSON_DESCRIPTION_VALUE))
                    .body(JSON_PROVIDER_KEY, is(PROVIDER_NAME))
                    .body(JSON_RETURN_URL_KEY, is(RETURN_URL))
                    .body("containsKey('card_details')", is(false))
                    .body("containsKey('gateway_account')", is(false))
                    .body("created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(.\\d{1,3})?Z"))
                    .body("created_date", isWithin(10, SECONDS))
                    .contentType(JSON);

        }
        
        @Test
        void shouldCreateCharge_whenReferenceIsACardNumber_forAPIPayment() throws JsonProcessingException {
            var cardInformation = aCardInformation().build();
            app.getCardidStub().returnCardInformation(VALID_CARD_NUMBER, cardInformation);

            app.givenSetup()
                    .body(toJson(Map.of(
                            JSON_AMOUNT_KEY, AMOUNT,
                            JSON_REFERENCE_KEY, VALID_CARD_NUMBER,
                            JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                            JSON_RETURN_URL_KEY, RETURN_URL,
                            JSON_SOURCE_KEY, CARD_API
                    )))
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(Status.CREATED.getStatusCode())
                    .body(JSON_REFERENCE_KEY, is(VALID_CARD_NUMBER))
                    .contentType(JSON);
        }

        @Test
        void shouldReturn404_whenServiceIdDoesNotExist() {
            app.givenSetup()
                    .body(toJson(Map.of(
                            JSON_AMOUNT_KEY, AMOUNT,
                            JSON_REFERENCE_KEY, VALID_CARD_NUMBER,
                            JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                            JSON_RETURN_URL_KEY, RETURN_URL
                    )))
                    .post(format("/v1/api/service/%s/account/%s/charges", NON_EXISTENT_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(NOT_FOUND.getStatusCode())
                    .body("message[0]", CoreMatchers.is("Gateway account not found for service ID [non-existent-service-id] and account type [test]"));
        }

        @Test
        void shouldReturn404_whenGatewayAccountNotFound() {
            app.givenSetup()
                    .body(toJson(Map.of(
                            JSON_AMOUNT_KEY, AMOUNT,
                            JSON_REFERENCE_KEY, VALID_CARD_NUMBER,
                            JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                            JSON_RETURN_URL_KEY, RETURN_URL
                    )))
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.LIVE))
                    .then()
                    .statusCode(NOT_FOUND.getStatusCode())
                    .body("message[0]", CoreMatchers.is("Gateway account not found for service ID [valid-service-id] and account type [live]"));;
        }

        @Test
        void shouldReturn400_whenReferenceIsACardNumber_forPaymentLinkPayment() throws JsonProcessingException {
            var cardInformation = aCardInformation().build();
            app.getCardidStub().returnCardInformation(VALID_CARD_NUMBER, cardInformation);

            app.givenSetup()
                    .body(toJson(Map.of(
                            JSON_AMOUNT_KEY, AMOUNT,
                            JSON_REFERENCE_KEY, VALID_CARD_NUMBER,
                            JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                            JSON_RETURN_URL_KEY, RETURN_URL,
                            JSON_SOURCE_KEY, CARD_PAYMENT_LINK
                    )))
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .contentType(JSON)
                    .statusCode(BAD_REQUEST.getStatusCode())
                    .body("error_identifier", is(CARD_NUMBER_IN_PAYMENT_LINK_REFERENCE_REJECTED.toString()))
                    .body("message[0]", is("Card number entered in a payment link reference"));
        }

        @Test
        void shouldReturn422_whenFieldsExceedMaxSize() {
            app.givenSetup()
                    .body(toJson(Map.of(
                            JSON_AMOUNT_KEY, AMOUNT,
                            JSON_REFERENCE_KEY, randomAlphabetic(256),
                            JSON_DESCRIPTION_KEY, randomAlphanumeric(256),
                            JSON_EMAIL_KEY, randomAlphanumeric(255),
                            JSON_RETURN_URL_KEY, RETURN_URL
                    )))
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(422)
                    .contentType(JSON)
                    .header("Location", is(nullValue()))
                    .body(JSON_CHARGE_KEY, is(nullValue()))
                    .body(JSON_MESSAGE_KEY, containsInAnyOrder(
                            "Field [email] can have a size between 0 and 254",
                            "Field [description] can have a size between 0 and 255",
                            "Field [reference] can have a size between 0 and 255"
                    ));
        }

        @Test
        void shouldReturn422_whenFieldsMissing() {
            app.givenSetup()
                    .body("{}")
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(422)
                    .contentType(JSON)
                    .header("Location", is(nullValue()))
                    .body(JSON_CHARGE_KEY, is(nullValue()))
                    .body(JSON_MESSAGE_KEY, containsInAnyOrder(
                            "Field [reference] cannot be null",
                            "Field [description] cannot be null",
                            "Field [amount] cannot be null"
                    ));
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
                            JSON_AMOUNT_KEY, AMOUNT,
                            JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                            JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                            JSON_RETURN_URL_KEY, RETURN_URL
                    )))
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(403)
                    .contentType(JSON)
                    .body(JSON_MESSAGE_KEY, contains("This gateway account is disabled"));
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
