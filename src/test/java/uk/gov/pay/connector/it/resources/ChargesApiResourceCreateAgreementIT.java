package uk.gov.pay.connector.it.resources;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.it.base.ChargingITestBaseExtension;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;
import uk.gov.pay.connector.queue.tasks.TaskQueue;
import uk.gov.pay.connector.util.AddAgreementParams;
import uk.gov.pay.connector.util.AddGatewayAccountParams;
import uk.gov.pay.connector.util.AddPaymentInstrumentParams;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.restassured.http.ContentType.JSON;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_USER_NOT_PRESENT_QUEUED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_CREATED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_STARTED;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.AMOUNT;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_AMOUNT_KEY;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_AUTH_MODE_KEY;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_CHARGE_KEY;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_DELAYED_CAPTURE_KEY;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_DESCRIPTION_KEY;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_DESCRIPTION_VALUE;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_EMAIL_KEY;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_LANGUAGE_KEY;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_METADATA_KEY;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_MOTO_KEY;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_PROVIDER_KEY;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_REFERENCE_KEY;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_REFERENCE_VALUE;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_RETURN_URL_KEY;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_SOURCE_KEY;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.RETURN_URL;
import static uk.gov.pay.connector.util.AddAgreementParams.AddAgreementParamsBuilder.anAddAgreementParams;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;
import static uk.gov.pay.connector.util.AddPaymentInstrumentParams.AddPaymentInstrumentParamsBuilder.anAddPaymentInstrumentParams;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class ChargesApiResourceCreateAgreementIT {
    @RegisterExtension
    public static ChargingITestBaseExtension app = new ChargingITestBaseExtension("sandbox");
    
    private Appender<ILoggingEvent> mockAppender = mock(Appender.class);
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor = ArgumentCaptor.forClass(LoggingEvent.class);

    private static final String JSON_AGREEMENT_ID_KEY = "agreement_id";
    private static final String JSON_SAVE_PAYMENT_INSTRUMENT_TO_AGREEMENT_KEY = "save_payment_instrument_to_agreement";
    private static final String JSON_VALID_AGREEMENT_ID_VALUE = "12345678901234567890123456";
    private static final String JSON_TOO_SHORT_AGREEMENT_ID_VALUE = "12345678901234567890";
    private static final String JSON_TOO_LONG_AGREEMENT_ID_VALUE = "123456789012345678901234567890";
    private static final String JSON_AUTH_MODE_AGREEMENT = "agreement";
    
    @BeforeEach
    void setUpLogger() {
        Logger root = (Logger) LoggerFactory.getLogger(TaskQueue.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
    }

    @Test
    void shouldCreatePaymentWithAgreementIdAndSavePaymentInstrumentToAgreementTrue() {
        app.getDatabaseTestHelper().enableRecurring(Long.valueOf(app.getAccountId()));
        AddAgreementParams agreementParams = anAddAgreementParams()
                .withGatewayAccountId(String.valueOf(app.getAccountId()))
                .withExternalAgreementId(JSON_VALID_AGREEMENT_ID_VALUE)
                .build();
        app.getDatabaseTestHelper().addAgreement(agreementParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                JSON_SAVE_PAYMENT_INSTRUMENT_TO_AGREEMENT_KEY, "true"
        ));

        String chargeId = app.getConnectorRestApiClient()
                .postCreateCharge(postBody)
                .statusCode(SC_CREATED)
                .body(JSON_AGREEMENT_ID_KEY, is(JSON_VALID_AGREEMENT_ID_VALUE))
                .contentType(JSON)
                .extract().path("charge_id");

        app.assertFrontendChargeStatusIs(chargeId, CREATED.getValue());
        app.assertApiStateIs(chargeId, EXTERNAL_CREATED.getStatus());
    }

    @Test
    void shouldCreatePaymentWithAgreementIdAndAuthorisationModeAgreement() {
        app.getDatabaseTestHelper().enableRecurring(Long.valueOf(app.getAccountId()));
        Long paymentInstrumentId = RandomUtils.nextLong();

        AddPaymentInstrumentParams paymentInstrumentParams = anAddPaymentInstrumentParams()
                .withPaymentInstrumentId(paymentInstrumentId)
                .withPaymentInstrumentStatus(PaymentInstrumentStatus.ACTIVE).build();
        app.getDatabaseTestHelper().addPaymentInstrument(paymentInstrumentParams);

        AddAgreementParams agreementParams = anAddAgreementParams()
                .withGatewayAccountId(app.getAccountId())
                .withExternalAgreementId(JSON_VALID_AGREEMENT_ID_VALUE)
                .withPaymentInstrumentId(paymentInstrumentId)
                .build();
        app.getDatabaseTestHelper().addAgreement(agreementParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                JSON_AUTH_MODE_KEY, JSON_AUTH_MODE_AGREEMENT
        ));

        String chargeId = app.getConnectorRestApiClient()
                .postCreateCharge(postBody)
                .statusCode(SC_CREATED)
                .body(JSON_AGREEMENT_ID_KEY, is(JSON_VALID_AGREEMENT_ID_VALUE))
                .contentType(JSON)
                .extract().path("charge_id");

        app.assertFrontendChargeStatusIs(chargeId, AUTHORISATION_USER_NOT_PRESENT_QUEUED.getValue());
        app.assertApiStateIs(chargeId, EXTERNAL_STARTED.getStatus());

        verify(mockAppender).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logEvents = loggingEventArgumentCaptor.getAllValues();

        LoggingEvent log = logEvents.get(0);
        List<String> logArguments = Arrays.stream(log.getArgumentArray()).map(String::valueOf).collect(Collectors.toUnmodifiableList());
        assertThat(log.getMessage(), is("Task added to queue"));
        assertThat(logArguments, hasItem("task_type=authorise_with_user_not_present"));
    }

    @Test
    void shouldReturn200_whenChargeAlreadyCreatedForIdempotencyKeyWithMatchingRequestBody_whenRequestBodyHasMinimalValues() {
        String idempotencyKey = "an-idempotency-key";

        app.getDatabaseTestHelper().enableRecurring(Long.valueOf(app.getAccountId()));
        Long paymentInstrumentId = RandomUtils.nextLong();

        AddPaymentInstrumentParams paymentInstrumentParams = anAddPaymentInstrumentParams()
                .withPaymentInstrumentId(paymentInstrumentId)
                .withPaymentInstrumentStatus(PaymentInstrumentStatus.ACTIVE).build();
        app.getDatabaseTestHelper().addPaymentInstrument(paymentInstrumentParams);

        AddAgreementParams agreementParams = anAddAgreementParams()
                .withGatewayAccountId(app.getAccountId())
                .withExternalAgreementId(JSON_VALID_AGREEMENT_ID_VALUE)
                .withPaymentInstrumentId(paymentInstrumentId)
                .build();
        app.getDatabaseTestHelper().addAgreement(agreementParams);

        String existingChargeExternalId = app.addCharge(CREATED);

        // IMPORTANT: Do not modify this request body if the test fails. If properties are modified/removed on the 
        // create charge request, changes to the business code should be made in a way that a request stored in the 
        // idempotency record will continue to be matched against a new request with the same payload coming from 
        // consumers of the API. If adding new properties to the request, any default value should be provided by the
        // getters on ChargeCreateRequest, with the @JsonIgnore annotation added.
        Map<String, Object> requestBodyMap = Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                JSON_AUTH_MODE_KEY, JSON_AUTH_MODE_AGREEMENT
        );

        app.getDatabaseTestHelper().insertIdempotency(idempotencyKey, Long.valueOf(app.getAccountId()), existingChargeExternalId, requestBodyMap);

        app.getConnectorRestApiClient()
                .withHeader("Idempotency-Key", idempotencyKey)
                .postCreateCharge(toJson(requestBodyMap))
                .statusCode(SC_OK)
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(existingChargeExternalId));
    }

    @Test
    void shouldReturn200_whenChargeAlreadyCreatedForIdempotencyKeyWithMatchingRequestBody_whenRequestBodyHasValuesForAllProperties() {
        String idempotencyKey = "an-idempotency-key";

        app.getDatabaseTestHelper().enableRecurring(Long.valueOf(app.getAccountId()));
        Long paymentInstrumentId = RandomUtils.nextLong();

        AddPaymentInstrumentParams paymentInstrumentParams = anAddPaymentInstrumentParams()
                .withPaymentInstrumentId(paymentInstrumentId)
                .withPaymentInstrumentStatus(PaymentInstrumentStatus.ACTIVE).build();
        app.getDatabaseTestHelper().addPaymentInstrument(paymentInstrumentParams);

        AddAgreementParams agreementParams = anAddAgreementParams()
                .withGatewayAccountId(app.getAccountId())
                .withExternalAgreementId(JSON_VALID_AGREEMENT_ID_VALUE)
                .withPaymentInstrumentId(paymentInstrumentId)
                .build();
        app.getDatabaseTestHelper().addAgreement(agreementParams);

        String existingChargeExternalId = app.addCharge(CREATED);

        // IMPORTANT: Do not modify this request body if the test fails. If properties are modified/removed on the 
        // create charge request, changes to the business code should be made in a way that a request stored in the 
        // idempotency record will continue to be matched against a new request with the same payload coming from 
        // consumers of the API.
        Map<String, Object> requestBodyMap = Map.ofEntries(
                Map.entry(JSON_AMOUNT_KEY, AMOUNT),
                Map.entry(JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE),
                Map.entry(JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE),
                Map.entry(JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE),
                Map.entry(JSON_AUTH_MODE_KEY, JSON_AUTH_MODE_AGREEMENT),
                Map.entry(JSON_EMAIL_KEY, "joe.blogs@example.org"),
                Map.entry(JSON_DELAYED_CAPTURE_KEY, Boolean.TRUE),
                Map.entry(JSON_LANGUAGE_KEY, "en"),
                Map.entry(JSON_SOURCE_KEY, "CARD_API"),
                Map.entry(JSON_MOTO_KEY, Boolean.TRUE),
                Map.entry(JSON_PROVIDER_KEY, "sandbox"),
                Map.entry(JSON_METADATA_KEY, Map.of("foo", "bar")));

        app.getDatabaseTestHelper().insertIdempotency(idempotencyKey, Long.valueOf(app.getAccountId()), existingChargeExternalId, requestBodyMap);

        app.getConnectorRestApiClient()
                .withHeader("Idempotency-Key", idempotencyKey)
                .postCreateCharge(toJson(requestBodyMap))
                .statusCode(SC_OK)
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(existingChargeExternalId));
    }

    @Test
    void shouldReturn409_whenChargeAlreadyCreatedForIdempotencyKey_andRequestBodyDoesNotMatch() {
        String idempotencyKey = "an-idempotency-key";

        app.getDatabaseTestHelper().enableRecurring(Long.valueOf(app.getAccountId()));
        Long paymentInstrumentId = RandomUtils.nextLong();

        AddPaymentInstrumentParams paymentInstrumentParams = anAddPaymentInstrumentParams()
                .withPaymentInstrumentId(paymentInstrumentId)
                .withPaymentInstrumentStatus(PaymentInstrumentStatus.ACTIVE).build();
        app.getDatabaseTestHelper().addPaymentInstrument(paymentInstrumentParams);

        AddAgreementParams agreementParams = anAddAgreementParams()
                .withGatewayAccountId(app.getAccountId())
                .withExternalAgreementId(JSON_VALID_AGREEMENT_ID_VALUE)
                .withPaymentInstrumentId(paymentInstrumentId)
                .build();
        app.getDatabaseTestHelper().addAgreement(agreementParams);

        String existingChargeExternalId = app.addCharge(CREATED);
        
        Map<String, Object> previousRequestBodyMap = Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                JSON_AUTH_MODE_KEY, JSON_AUTH_MODE_AGREEMENT
        );
        Map<String, Object> newRequestBodyMap = Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, "A DIFFERENT REF",
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                JSON_AUTH_MODE_KEY, JSON_AUTH_MODE_AGREEMENT
        );

        app.getDatabaseTestHelper().insertIdempotency(idempotencyKey, Long.valueOf(app.getAccountId()), existingChargeExternalId, previousRequestBodyMap);

        app.getConnectorRestApiClient()
                .withHeader("Idempotency-Key", idempotencyKey)
                .postCreateCharge(toJson(newRequestBodyMap))
                .statusCode(SC_CONFLICT)
                .contentType(JSON)
                .body("message", contains("The Idempotency-Key has already been used to create a payment"));
    }

    @Test
    void shouldReturn400WhenSavePaymentInstrumentToAgreementIsTrueButNoAgreementId() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("worldpay")
                .withAccountId(accountId)
                .withRecurringEnabled(true)
                .build();
        app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_SAVE_PAYMENT_INSTRUMENT_TO_AGREEMENT_KEY,
                "true"
        ));

        app.getConnectorRestApiClient()
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_BAD_REQUEST)
                .contentType(JSON)
                .body("message", contains("If [save_payment_instrument_to_agreement] is true, [agreement_id] must be specified"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    void shouldReturn400WhenSavePaymentInstrumentToAgreementTrueAndAgreementNotFound() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("worldpay")
                .withAccountId(accountId)
                .withRecurringEnabled(true)
                .build();
        app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                JSON_SAVE_PAYMENT_INSTRUMENT_TO_AGREEMENT_KEY, "true"
        ));

        app.getConnectorRestApiClient()
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_BAD_REQUEST)
                .contentType(JSON)
                .body("error_identifier", is(ErrorIdentifier.AGREEMENT_NOT_FOUND.toString()));
    }

    @Test
    void shouldReturn422WhenAuthorisationModeAgreementButNoAgreementId() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("worldpay")
                .withAccountId(accountId)
                .withRecurringEnabled(true)
                .build();
        app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_AUTH_MODE_KEY, JSON_AUTH_MODE_AGREEMENT
        ));

        app.getConnectorRestApiClient()
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_UNPROCESSABLE_ENTITY)
                .contentType(JSON)
                .body("message", contains("Missing mandatory attribute: agreement_id"))
                .body("error_identifier", is(ErrorIdentifier.MISSING_MANDATORY_ATTRIBUTE.toString()));
    }

    @Test
    void shouldReturn422WhenAuthorisationModeAgreementAndReturnUrlProvided() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("sandbox")
                .withAccountId(accountId)
                .withRecurringEnabled(true)
                .build();
        app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_AUTH_MODE_KEY, JSON_AUTH_MODE_AGREEMENT
        ));

        app.getConnectorRestApiClient()
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_UNPROCESSABLE_ENTITY)
                .contentType(JSON)
                .body("message", contains("Unexpected attribute: return_url"))
                .body("error_identifier", is(ErrorIdentifier.UNEXPECTED_ATTRIBUTE.toString()));
    }

    @Test
    void shouldReturn422WhenAuthorisationModeAgreementAndMotoTrue() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("sandbox")
                .withAccountId(accountId)
                .withAllowMoto(true)
                .withRecurringEnabled(true)
                .build();
        app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_AUTH_MODE_KEY, JSON_AUTH_MODE_AGREEMENT,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                JSON_MOTO_KEY, true
        ));

        app.getConnectorRestApiClient()
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_UNPROCESSABLE_ENTITY)
                .contentType(JSON)
                .body("message", contains("Unexpected attribute: moto"))
                .body("error_identifier", is(ErrorIdentifier.UNEXPECTED_ATTRIBUTE.toString()));
    }

    @Test
    void shouldReturn422WhenAuthorisationModeAgreementAndEmailProvided() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("sandbox")
                .withAccountId(accountId)
                .withRecurringEnabled(true)
                .build();
        app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_AUTH_MODE_KEY, JSON_AUTH_MODE_AGREEMENT,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                JSON_EMAIL_KEY, "test@test.test"
        ));

        app.getConnectorRestApiClient()
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_UNPROCESSABLE_ENTITY)
                .contentType(JSON)
                .body("message", contains("Unexpected attribute: email"))
                .body("error_identifier", is(ErrorIdentifier.UNEXPECTED_ATTRIBUTE.toString()));
    }

    @Test
    void shouldReturn422WhenAuthorisationModeAgreementAndPrefilledCardholderDetailsProvided() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("sandbox")
                .withAccountId(accountId)
                .withRecurringEnabled(true)
                .build();
        app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_AUTH_MODE_KEY, JSON_AUTH_MODE_AGREEMENT,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                "prefilled_cardholder_details", Map.of(
                        "cardholder_name", "Gwen Denise Smith",
                        "billing_address", Map.of(
                                "line1", "The White Chapel Building",
                                "line2", "10 Whitechapel High Street",
                                "city", "London",
                                "postcode", "E1 8QS",
                                "country", "GB"
                        )
                )
        ));

        app.getConnectorRestApiClient()
                .postCreateCharge(postBody,accountId)
                .statusCode(SC_UNPROCESSABLE_ENTITY)
                .contentType(JSON)
                .body("message", contains("Unexpected attribute: prefilled_cardholder_details"))
                .body("error_identifier", is(ErrorIdentifier.UNEXPECTED_ATTRIBUTE.toString()));
    }

    @Test
    void shouldReturn400WhenAuthorisationModeIsAgreementAndAgreementNotFound() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("worldpay")
                .withAccountId(accountId)
                .withRecurringEnabled(true)
                .build();
        app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                "authorisation_mode", "agreement"
        ));

        app.getConnectorRestApiClient()
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_BAD_REQUEST)
                .body("message", contains("Agreement with ID [" + JSON_VALID_AGREEMENT_ID_VALUE + "] not found."))
                .body("error_identifier", is(ErrorIdentifier.AGREEMENT_NOT_FOUND.toString()));
    }

    @Test
    void shouldReturn400WhenAuthorisationModeIsAgreementAndPaymentInstrumentNotFound() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("worldpay")
                .withAccountId(accountId)
                .withRecurringEnabled(true)
                .build();
        app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);

        AddAgreementParams agreementParams = anAddAgreementParams()
                .withGatewayAccountId(accountId)
                .withExternalAgreementId(JSON_VALID_AGREEMENT_ID_VALUE)
                .build();
        app.getDatabaseTestHelper().addAgreement(agreementParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                "authorisation_mode", "agreement"
        ));

        app.getConnectorRestApiClient()
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_BAD_REQUEST)
                .body("message", contains("Agreement with ID [" + JSON_VALID_AGREEMENT_ID_VALUE + "] does not have a payment instrument"))
                .body("error_identifier", is(ErrorIdentifier.AGREEMENT_NOT_ACTIVE.toString()));
    }

    @Test
    void shouldReturn400WhenAuthorisationModeIsAgreementAndPaymentInstrumentIsNotActive() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("worldpay")
                .withAccountId(accountId)
                .withRecurringEnabled(true)
                .build();
        long paymentInstrumentId = 11L;
        app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);
        String paymentInstrumentExternalId = "this-has-status-created";
        AddPaymentInstrumentParams addPaymentInstrumentParams = anAddPaymentInstrumentParams()
                .withPaymentInstrumentId(paymentInstrumentId)
                .withExternalPaymentInstrumentId(paymentInstrumentExternalId)
                .withPaymentInstrumentStatus(PaymentInstrumentStatus.CREATED)
                .build();
        app.getDatabaseTestHelper().addPaymentInstrument(addPaymentInstrumentParams);
        AddAgreementParams agreementParams = anAddAgreementParams()
                .withGatewayAccountId(accountId)
                .withExternalAgreementId(JSON_VALID_AGREEMENT_ID_VALUE)
                .withPaymentInstrumentId(paymentInstrumentId)
                .build();
        app.getDatabaseTestHelper().addAgreement(agreementParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                "authorisation_mode", "agreement"
        ));

        app.getConnectorRestApiClient()
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_BAD_REQUEST)
                .body("message", contains("Agreement with ID [" + JSON_VALID_AGREEMENT_ID_VALUE + "] has payment instrument with ID ["
                        + paymentInstrumentExternalId + "] but its state is [" + PaymentInstrumentStatus.CREATED + "]"))
                .body("error_identifier", is(ErrorIdentifier.AGREEMENT_NOT_ACTIVE.toString()));

    }

    @Test
    void shouldReturn400WhenAuthorisationModeMotoApiAndAgreementId() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("worldpay")
                .withAccountId(accountId)
                .withAllowMoto(true)
                .withAllowAuthApi(true)
                .build();
        app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                JSON_AUTH_MODE_KEY, "moto_api"
        ));

        app.getConnectorRestApiClient()
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_UNPROCESSABLE_ENTITY)
                .contentType(JSON)
                .body("message", contains("Unexpected attribute: agreement_id"))
                .body("error_identifier", is(ErrorIdentifier.UNEXPECTED_ATTRIBUTE.toString()));
    }

    @Test
    void shouldReturn400WhenSavePaymentInstrumentToAgreementTrueAuthorisationModeAgreementAndAgreementId() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("worldpay")
                .withAccountId(accountId)
                .withRecurringEnabled(true)
                .build();
        app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                JSON_SAVE_PAYMENT_INSTRUMENT_TO_AGREEMENT_KEY, true,
                JSON_AUTH_MODE_KEY, JSON_AUTH_MODE_AGREEMENT
        ));

        app.getConnectorRestApiClient()
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_BAD_REQUEST)
                .contentType(JSON)
                .body("message", contains("If [save_payment_instrument_to_agreement] is true, [authorisation_mode] must be [web]"))
                .body("error_identifier", is(ErrorIdentifier.INCORRECT_AUTHORISATION_MODE_FOR_SAVE_PAYMENT_INSTRUMENT_TO_AGREEMENT.toString()));
    }

    @Test
    void shouldReturn400WhenAgreementIdIsProvidedButNotSavePaymentInstrumentToAgreementNorAuthorisationModeAgreement() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("worldpay")
                .withAccountId(accountId)
                .build();
        app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE
        ));

        app.getConnectorRestApiClient()
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_UNPROCESSABLE_ENTITY)
                .contentType(JSON)
                .body("message", contains("Unexpected attribute: agreement_id"))
                .body("error_identifier", is(ErrorIdentifier.UNEXPECTED_ATTRIBUTE.toString()));
    }

    @Test
    void shouldReturn400WhenAgreementIdIsProvidedButSavePaymentInstrumentToAgreementFalse() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("worldpay")
                .withAccountId(accountId)
                .build();
        app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                JSON_SAVE_PAYMENT_INSTRUMENT_TO_AGREEMENT_KEY, false
        ));

        app.getConnectorRestApiClient()
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_UNPROCESSABLE_ENTITY)
                .contentType(JSON)
                .body("message", contains("Unexpected attribute: agreement_id"))
                .body("error_identifier", is(ErrorIdentifier.UNEXPECTED_ATTRIBUTE.toString()));
    }

    @Test
    void shouldReturn422WhenAgreementIdIdExceed26Characters() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("worldpay")
                .withAccountId(accountId)
                .build();
        app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);
        AddAgreementParams agreementParams = anAddAgreementParams()
                .withGatewayAccountId(app.getAccountId())
                .withExternalAgreementId(JSON_TOO_LONG_AGREEMENT_ID_VALUE)
                .build();
        app.getDatabaseTestHelper().addAgreement(agreementParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_AGREEMENT_ID_KEY, JSON_TOO_LONG_AGREEMENT_ID_VALUE,
                JSON_SAVE_PAYMENT_INSTRUMENT_TO_AGREEMENT_KEY,
                "true"
        ));

        app.getConnectorRestApiClient()
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_UNPROCESSABLE_ENTITY)
                .contentType(JSON);
    }

    @Test
    void shouldReturn422WhenAgreementIdIdFewerThan26Characters() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("worldpay")
                .withAccountId(accountId)
                .build();
        app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);
        AddAgreementParams agreementParams = anAddAgreementParams()
                .withGatewayAccountId(accountId)
                .withExternalAgreementId(JSON_TOO_SHORT_AGREEMENT_ID_VALUE)
                .build();
        app.getDatabaseTestHelper().addAgreement(agreementParams);
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_AGREEMENT_ID_KEY, JSON_TOO_SHORT_AGREEMENT_ID_VALUE,
                JSON_SAVE_PAYMENT_INSTRUMENT_TO_AGREEMENT_KEY,
                "true"
        ));

        app.getConnectorRestApiClient()
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_UNPROCESSABLE_ENTITY)
                .contentType(JSON);
    }

    @Test
    void shouldReturn422OnSavePaymentToInstrumentRequestWhenRecurringNotEnabledForGatewayAccount() {
        AddAgreementParams agreementParams = anAddAgreementParams()
                .withGatewayAccountId(app.getAccountId())
                .withExternalAgreementId(JSON_VALID_AGREEMENT_ID_VALUE)
                .build();
        app.getDatabaseTestHelper().addAgreement(agreementParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                JSON_SAVE_PAYMENT_INSTRUMENT_TO_AGREEMENT_KEY, "true"
        ));

        app.getConnectorRestApiClient()
                .postCreateCharge(postBody)
                .statusCode(SC_UNPROCESSABLE_ENTITY)
                .contentType(JSON)
                .body("message", contains("Recurring payment agreements are not enabled on this account"))
                .body("error_identifier", is(ErrorIdentifier.RECURRING_CARD_PAYMENTS_NOT_ALLOWED.toString()));
    }
}
