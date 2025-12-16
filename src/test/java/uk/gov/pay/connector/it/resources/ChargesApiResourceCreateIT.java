package uk.gov.pay.connector.it.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.restassured.response.ValidatableResponse;
import jakarta.ws.rs.core.Response.Status;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;
import uk.gov.pay.connector.util.AddAgreementParams;
import uk.gov.pay.connector.util.AddPaymentInstrumentParams;
import uk.gov.service.payments.commons.model.AgreementPaymentType;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.dropwizard.testing.ConfigOverride.config;
import static io.restassured.http.ContentType.JSON;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.OK;
import static java.lang.String.format;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
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
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_STARTED;
import static uk.gov.pay.connector.events.model.ResourceType.AGREEMENT;
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
import static uk.gov.pay.connector.util.AddAgreementParams.AddAgreementParamsBuilder.anAddAgreementParams;
import static uk.gov.pay.connector.util.AddPaymentInstrumentParams.AddPaymentInstrumentParamsBuilder.anAddPaymentInstrumentParams;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.NumberMatcher.isNumber;
import static uk.gov.pay.connector.util.RandomTestDataGeneratorUtils.secureRandomLong;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.CARD_NUMBER_IN_PAYMENT_LINK_REFERENCE_REJECTED;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.NON_HTTPS_RETURN_URL_NOT_ALLOWED_FOR_A_LIVE_ACCOUNT;
import static uk.gov.service.payments.commons.model.Source.CARD_API;
import static uk.gov.service.payments.commons.model.Source.CARD_PAYMENT_LINK;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class ChargesApiResourceCreateIT {

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension(
            config("eventQueue.eventQueueEnabled", "true"),
            config("captureProcessConfig.backgroundProcessingEnabled", "true"));

    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("sandbox", app.getLocalPort(), app.getDatabaseTestHelper());

    private static final String VALID_CARD_NUMBER = "4242424242424242";
    private static final String VALID_SERVICE_ID = "valid-service-id";
    private String testGatewayAccountId;
    private String liveGatewayAccountId;

    @BeforeEach
    void setup() {
        testGatewayAccountId = app.givenSetup()
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
                        "service_id", "a-service-id",
                        "type", GatewayAccountType.LIVE,
                        "payment_provider", PaymentGatewayName.WORLDPAY.getName(),
                        "service_name", "my-test-service-name"
                )))
                .post("/v1/api/accounts")
                .then()
                .statusCode(201)
                .extract().path("gateway_account_id");
    }

    @Nested
    class ByGatewayAccountId {

        @Test
        void should_create_charge_and_retrieve_details_successfully() {
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
                    .post(format("/v1/api/accounts/%s/charges", testGatewayAccountId))
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
            String documentLocation = expectedChargeLocationFor(testGatewayAccountId, testChargeId);
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
                    .get(format("/v1/api/accounts/%s/charges/%s", testGatewayAccountId, testChargeId))
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
                    .body("refund_summary.status", is("pending"))
                    .body("agreement_payment_type", is(nullValue()));

            // Reload the charge token as it should have changed
            String newChargeTokenId = app.getDatabaseTestHelper().getChargeTokenByExternalChargeId(testChargeId);

            getChargeResponse
                    .body("links", hasSize(4))
                    .body("links", containsLink("self", "GET", documentLocation))
                    .body("links", containsLink("refunds", "GET", documentLocation + "/refunds"))
                    .body("links", containsLink("next_url", "GET", "http://CardFrontend/secure/" + newChargeTokenId))
                    .body("links", containsLink("next_url_post", "POST", "http://CardFrontend/secure",
                            "application/x-www-form-urlencoded", Map.of("chargeTokenId", newChargeTokenId)));


            String expectedGatewayAccountCredentialId = app.getDatabaseTestHelper().getGatewayAccountCredentialsForAccount(Long.parseLong(testGatewayAccountId)).getFirst().get("id").toString();
            String actualGatewayAccountCredentialId = app.getDatabaseTestHelper().getChargeByExternalId(testChargeId).get("gateway_account_credential_id").toString();

            assertThat(actualGatewayAccountCredentialId, is(expectedGatewayAccountCredentialId));
        }

        @Test
        void should_create_charge_with_authorisation_mode_moto_api() {
            app.givenSetup()
                    .body(toJson(Map.of("op", "replace", "path", "allow_authorisation_api", "value", true)))
                    .patch(format("/v1/api/accounts/%s", testGatewayAccountId))
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
                    .post(format("/v1/api/accounts/%s/charges", testGatewayAccountId))
                    .then()
                    .statusCode(201)
                    .extract().path("charge_id");

            ValidatableResponse getChargeResponse = app.givenSetup()
                    .get(format("/v1/api/accounts/%s/charges/%s", testGatewayAccountId, testChargeId))
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
        void should_create_charge_with_no_email_field() {
            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "Test reference",
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/"
                    )))
                    .post(format("/v1/api/accounts/%s/charges", testGatewayAccountId))
                    .then()
                    .statusCode(Status.CREATED.getStatusCode())
                    .body("charge_id", is(notNullValue()))
                    .body("email", is(nullValue()))
                    .contentType(JSON);
        }

        @Test
        void should_create_charge_when_reference_is_a_card_number_for_api_payment() throws JsonProcessingException {
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
                    .post(format("/v1/api/accounts/%s/charges", testGatewayAccountId))
                    .then()
                    .statusCode(Status.CREATED.getStatusCode())
                    .body("reference", is(VALID_CARD_NUMBER))
                    .contentType(JSON);
        }

        @ParameterizedTest
        @CsvSource(nullValues = "null", textBlock = """
                instalment,INSTALMENT, 
                recurring,RECURRING, 
                unscheduled,UNSCHEDULED
                null, RECURRING
                """)
        void should_create_recurring_charge_with_agreement_type_and_retrieve_details_successfully(String requestAgreementPaymentType, AgreementPaymentType expectedAgreementPaymentType) {
            app.getDatabaseTestHelper().enableRecurring(Long.parseLong(testGatewayAccountId));
            Long paymentInstrumentId = secureRandomLong();

            AddPaymentInstrumentParams paymentInstrumentParams = anAddPaymentInstrumentParams()
                    .withPaymentInstrumentId(paymentInstrumentId)
                    .withPaymentInstrumentStatus(PaymentInstrumentStatus.ACTIVE).build();
            app.getDatabaseTestHelper().addPaymentInstrument(paymentInstrumentParams);

            String agreementId = "12345678901234567890123456";
            AddAgreementParams agreementParams = anAddAgreementParams()
                    .withGatewayAccountId(testGatewayAccountId)
                    .withExternalAgreementId(agreementId)
                    .withPaymentInstrumentId(paymentInstrumentId)
                    .build();
            app.getDatabaseTestHelper().addAgreement(agreementParams);

            Map<String, Object> payloadMap = new HashMap<>();

            payloadMap.put("amount", 6234L);
            payloadMap.put("reference", "Test reference");
            payloadMap.put("description", "Test description");
            payloadMap.put("authorisation_mode", "agreement");
            payloadMap.put("agreement_id", agreementId);
            payloadMap.put("agreement_payment_type", requestAgreementPaymentType);

            ValidatableResponse createResponse = app.givenSetup()
                    .body(toJson(payloadMap))
                    .post(format("/v1/api/accounts/%s/charges", testGatewayAccountId))
                    .then()
                    .statusCode(Status.CREATED.getStatusCode())
                    .body("charge_id", is(notNullValue()))
                    .body("amount", isNumber(6234L))
                    .body("reference", is("Test reference"))
                    .body("description", is("Test description"))
                    .body("payment_provider", is("sandbox"))
                    .body("delayed_capture", is(false))
                    .body("moto", is(false))
                    .body("state.status", is(CREATED.toExternal().getStatus()))
                    .body("created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(.\\d{1,3})?Z"))
                    .body("authorisation_mode", is("agreement"))
                    .body("containsKey('card_details')", is(false))
                    .body("agreement_payment_type", is(expectedAgreementPaymentType.getName()))
                    .contentType(JSON);

            String testChargeId = createResponse.extract().path("charge_id");

            app.givenSetup()
                    .get(format("/v1/api/accounts/%s/charges/%s", testGatewayAccountId, testChargeId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .contentType(JSON)
                    .body("charge_id", is(notNullValue()))
                    .body("amount", isNumber(6234L))
                    .body("reference", is("Test reference"))
                    .body("description", is("Test description"))
                    .body("payment_provider", is("sandbox"))
                    .body("$", not(hasKey("metadata")))
                    .body("delayed_capture", is(false))
                    .body("moto", is(false))
                    .body("state.status", is(EXTERNAL_STARTED.getStatus()))
                    .body("created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(.\\d{1,3})?Z"))
                    .body("authorisation_mode", is(AGREEMENT.getLowercase()))
                    .body("containsKey('card_details')", is(false))
                    .body("agreement_payment_type", is(expectedAgreementPaymentType.getName()));
        }

        @Nested
        class BadRequest {
            @Test
            void when_reference_is_a_card_number_for_payment_link_payment() throws JsonProcessingException {
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
                        .post(format("/v1/api/accounts/%s/charges", testGatewayAccountId))
                        .then()
                        .contentType(JSON)
                        .statusCode(BAD_REQUEST.getStatusCode())
                        .body("error_identifier", is(CARD_NUMBER_IN_PAYMENT_LINK_REFERENCE_REJECTED.toString()))
                        .body("message[0]", is("Card number entered in a payment link reference"));
            }
        }

        @Nested
        class ReturnUnprocessableContent {

            @Test
            void when_return_url_is_not_https_when_gateway_account_is_live() {
                app.givenSetup()
                        .body(toJson(Map.of(
                                "amount", 6234L,
                                "reference", "Test reference",
                                "description", "Test description",
                                "email", "test@example.com",
                                "return_url", "http://service.local/success-page/",
                                "source", CARD_API)
                        ))
                        .post(format("/v1/api/accounts/%s/charges", liveGatewayAccountId))
                        .then()
                        .statusCode(SC_UNPROCESSABLE_ENTITY)
                        .body("error_identifier", is(NON_HTTPS_RETURN_URL_NOT_ALLOWED_FOR_A_LIVE_ACCOUNT.toString()))
                        .body("message[0]", is(format("Gateway account %s is LIVE, but is configured to use a " +
                                "non-https return_url", liveGatewayAccountId)));
            }

            @Test
            void when_moto_is_true_and_moto_not_allowed_for_account() {
                //by default, gateway account does not have moto enabled
                app.givenSetup()
                        .body(toJson(Map.of(
                                "amount", 6234L,
                                "reference", "Test reference",
                                "description", "Test description",
                                "return_url", "http://service.local/success-page/",
                                "moto", true
                        )))
                        .post(format("/v1/api/accounts/%s/charges", testGatewayAccountId))
                        .then()
                        .statusCode(422)
                        .contentType(JSON)
                        .body("message", contains("MOTO payments are not enabled for this gateway account"))
                        .body("error_identifier", is(ErrorIdentifier.MOTO_NOT_ALLOWED.toString()));
                ;
            }

            @Test
            void when_amount_is_zero_and_account_does_not_allow_zero_amount() {
                //by default, gateway account does not have zero amount enabled
                app.givenSetup()
                        .body(toJson(Map.of(
                                "amount", 0,
                                "reference", "Test reference",
                                "description", "Test description",
                                "return_url", "http://service.local/success-page/"
                        )))
                        .post(format("/v1/api/accounts/%s/charges", testGatewayAccountId))
                        .then()
                        .statusCode(422)
                        .contentType(JSON)
                        .body("message", contains("Zero amount charges are not enabled for this gateway account"))
                        .body("error_identifier", is(ErrorIdentifier.ZERO_AMOUNT_NOT_ALLOWED.toString()));
            }

            @ParameterizedTest
            @ValueSource(strings = {"CARD_API", "CARD_PAYMENT_LINK", "CARD_AGENT_INITIATED_MOTO"})
            void when_amount_is_under_30p_for_api_payment_for_Stripe_account(String source) {
                DatabaseFixtures.TestAccount stripeTestAccount = app.getDatabaseFixtures()
                        .aTestAccount()
                        .withPaymentProvider("stripe")
                        .withCredentials(Collections.singletonMap("stripe_account_id", "acct_123example123"))
                        .insert();

                app.givenSetup()
                        .body(toJson(Map.of(
                                "amount", 29,
                                "reference", "Test reference",
                                "description", "Test description",
                                "return_url", "http://service.local/success-page/",
                                "source", source
                        )))
                        .post(format("/v1/api/accounts/%s/charges", stripeTestAccount.getAccountId()))
                        .then()
                        .statusCode(422)
                        .contentType(JSON)
                        .body("message", contains("Payments under 30 pence are not allowed for Stripe accounts"))
                        .body("error_identifier", is(ErrorIdentifier.AMOUNT_BELOW_MINIMUM.toString()));
            }
        }

        @Test
        void should_create_charge_with_external_metadata() {
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
                    .post(format("/v1/api/accounts/%s/charges", testGatewayAccountId))
                    .then()
                    .statusCode(Status.CREATED.getStatusCode())
                    .contentType(JSON)
                    .body("metadata.key1", is("string"))
                    .body("metadata.key2", is(true))
                    .body("metadata.key3", is(123))
                    .body("metadata.key4", is(1.23F))
                    .extract().path("charge_id");

            app.givenSetup()
                    .get(format("/v1/api/accounts/%s/charges/%s", testGatewayAccountId, chargeExternalId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("metadata.key1", is("string"))
                    .body("metadata.key2", is(true))
                    .body("metadata.key3", is(123))
                    .body("metadata.key4", is(1.23F));

        }

        @Test
        void should_create_charge_with_null_metadata_because_null_values_are_not_deserialised() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("amount", 6234L);
            payload.put("reference", "Test reference");
            payload.put("description", "Test description");
            payload.put("return_url", "http://service.local/success-page/");
            payload.put("metadata", null);

            app.givenSetup()
                    .body(toJson(payload))
                    .post(format("/v1/api/accounts/%s/charges", testGatewayAccountId))
                    .then()
                    .statusCode(Status.CREATED.getStatusCode())
                    .body("reference", is("Test reference"));
        }


        @Test
        void should_create_moto_charge_when_moto_is_true_and_moto_allowed_for_account() {
            var payload = Map.of(
                    "op", "replace",
                    "path", "allow_moto",
                    "value", true);

            app.givenSetup()
                    .body(toJson(payload))
                    .patch("/v1/api/accounts/" + testGatewayAccountId)
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
                    .post(format("/v1/api/accounts/%s/charges", testGatewayAccountId))
                    .then()
                    .statusCode(201)
                    .contentType(JSON)
                    .body("moto", is(true));
        }

        @Test
        void should_create_charge_when_amount_is_zero_and_account_allows_zero_amount() {
            var payload = Map.of(
                    "op", "replace",
                    "path", "allow_zero_amount",
                    "value", true);

            app.givenSetup()
                    .body(toJson(payload))
                    .patch("/v1/api/accounts/" + testGatewayAccountId)
                    .then()
                    .statusCode(OK.getStatusCode());

            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 0,
                            "reference", "Test reference",
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/"
                    )))
                    .post(format("/v1/api/accounts/%s/charges", testGatewayAccountId))
                    .then()
                    .statusCode(201)
                    .contentType(JSON)
                    .body("amount", is(0));
        }

        @Test
        void should_return_404_when_account_not_found() {
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
        void should_return_403_when_account_disabled() {
            app.givenSetup()
                    .body(toJson(Map.of("op", "replace", "path", "disabled", "value", true)))
                    .patch(format("/v1/api/accounts/%s", testGatewayAccountId))
                    .then()
                    .statusCode(Status.OK.getStatusCode());

            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "Test reference",
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/"
                    )))
                    .post(format("/v1/api/accounts/%s/charges", testGatewayAccountId))
                    .then()
                    .statusCode(403)
                    .contentType(JSON)
                    .body(JSON_MESSAGE_KEY, contains("This gateway account is disabled"));
        }
    }

    @Nested
    class ByServiceIdAndAccountType {

        @Test
        void should_create_charge_and_retrieve_details_successfully() {
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
            String documentLocation = expectedChargeLocationFor(testGatewayAccountId, testChargeId);
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


            String expectedGatewayAccountCredentialId = app.getDatabaseTestHelper().getGatewayAccountCredentialsForAccount(Long.parseLong(testGatewayAccountId)).getFirst().get("id").toString();
            String actualGatewayAccountCredentialId = app.getDatabaseTestHelper().getChargeByExternalId(testChargeId).get("gateway_account_credential_id").toString();

            assertThat(actualGatewayAccountCredentialId, is(expectedGatewayAccountCredentialId));
        }

        @Test
        void should_create_charge_with_authorisation_mode_moto_api() {
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
        void should_create_charge_with_no_email_field() {
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
        void should_create_charge_when_reference_is_a_card_number_for_api_payment() throws JsonProcessingException {
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
        void should_return_400_when_reference_is_a_card_number_for_payment_link_payment() throws JsonProcessingException {
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
        void should_create_charge_with_external_metadata() {
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
        void should_create_charge_with_null_metadata_because_null_values_are_not_deserialised() {
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
        void should_create_moto_charge_when_moto_is_true_and_moto_allowed_for_account() {
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

        @Nested
        class ReturnUnprocessableContent {

            @Test
            void when_return_url_is_not_https_and_gateway_account_is_live() {
                app.givenSetup()
                        .body(toJson(Map.of(
                                "amount", 6234L,
                                "reference", "Test reference",
                                "description", "Test description",
                                "email", "test@example.com",
                                "return_url", "http://service.local/success-page/",
                                "source", CARD_API)
                        ))
                        .post(format("/v1/api/service/%s/account/%s/charges", "a-service-id", GatewayAccountType.LIVE))
                        .then()
                        .statusCode(SC_UNPROCESSABLE_ENTITY)
                        .body("error_identifier", is(NON_HTTPS_RETURN_URL_NOT_ALLOWED_FOR_A_LIVE_ACCOUNT.toString()))
                        .body("message[0]", is(format("Gateway account %s is LIVE, but is configured to use a " +
                                "non-https return_url", liveGatewayAccountId)));
            }

            @Test
            void when_moto_is_true_and_moto_not_allowed_for_account() {
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
                        .body("error_identifier", is(ErrorIdentifier.MOTO_NOT_ALLOWED.toString()));
                ;
            }

            @Test
            void when_amount_is_zero_if_account_does_not_allow_zero_amount() {
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

            @ParameterizedTest
            @ValueSource(strings = {"CARD_API", "CARD_PAYMENT_LINK", "CARD_AGENT_INITIATED_MOTO"})
            void when_amount_is_under_30p_for_api_payment_for_Stripe_account(String source) {
                DatabaseFixtures.TestAccount stripeTestAccount = app.getDatabaseFixtures()
                        .aTestAccount()
                        .withPaymentProvider("stripe")
                        .withServiceId(VALID_SERVICE_ID)
                        .withCredentials(Collections.singletonMap("stripe_account_id", "acct_123example123"))
                        .insert();

                app.givenSetup()
                        .body(toJson(Map.of(
                                "amount", 29,
                                "reference", "Test reference",
                                "description", "Test description",
                                "return_url", "http://service.local/success-page/",
                                "source", source
                        )))
                        .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                        .then()
                        .statusCode(422)
                        .contentType(JSON)
                        .body("message", contains("Payments under 30 pence are not allowed for Stripe accounts"))
                        .body("error_identifier", is(ErrorIdentifier.AMOUNT_BELOW_MINIMUM.toString()));
            }
        }

        @Test
        void should_create_charge_when_amount_is_zero_and_account_allows_zero_amount() {
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
        void should_return_404_when_service_id_does_not_exist() {
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
                    .body("message[0]", CoreMatchers.is("Gateway account not found for service external id [non-existent-service-id] and account type [test]"));
        }

        @Test
        void should_return_404_when_account_not_found() {
            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "4242424242424242",
                            "description", "Test description",
                            "return_url", "https://service.local/success-page/"
                    )))
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.LIVE))
                    .then()
                    .statusCode(NOT_FOUND.getStatusCode())
                    .body("message[0]", CoreMatchers.is("Gateway account not found for service external id [valid-service-id] and account type [live]"));
            ;
        }

        @Test
        void should_return_404_when_account_disabled() {
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
                    .statusCode(404)
                    .contentType(JSON)
                    .body("message", contains(format("Gateway account not found for service external id [%s] and account type [test]", VALID_SERVICE_ID)));
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

        final Message message = messages.getFirst();
        ZonedDateTime eventTimestamp = ZonedDateTime.parse(
                JsonParser.parseString(message.body())
                        .getAsJsonObject()
                        .get("timestamp")
                        .getAsString()
        );

        Optional<JsonObject> createdMessage = messages.stream()
                .map(m -> JsonParser.parseString(m.body()).getAsJsonObject())
                .filter(e -> e.get("event_type").getAsString().equals("PAYMENT_CREATED"))
                .findFirst();
        assertThat(createdMessage.isPresent(), is(true));
        assertThat(eventTimestamp, is(within(200, MILLIS, persistedCreatedDate)));
    }

    private List<Message> readMessagesFromEventQueue() {
        SqsClient sqsClient = app.getInstanceFromGuiceContainer(SqsClient.class);

        ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                .queueUrl(app.getEventQueueUrl())
                .messageAttributeNames("All")
                .waitTimeSeconds(1)
                .maxNumberOfMessages(10)
                .build();


        ReceiveMessageResponse receiveMessageResult = sqsClient.receiveMessage(receiveMessageRequest);

        return receiveMessageResult.messages();
    }

    private String expectedChargeLocationFor(String accountId, String chargeId) {
        return "https://localhost:" + app.getLocalPort() + "/v1/api/accounts/{accountId}/charges/{chargeId}"
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId);
    }

}
