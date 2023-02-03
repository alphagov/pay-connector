package uk.gov.pay.connector.it.resources;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
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
import static org.apache.http.HttpStatus.SC_CREATED;
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
import static uk.gov.pay.connector.util.AddAgreementParams.AddAgreementParamsBuilder.anAddAgreementParams;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;
import static uk.gov.pay.connector.util.AddPaymentInstrumentParams.AddPaymentInstrumentParamsBuilder.anAddPaymentInstrumentParams;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(
        app = ConnectorApp.class,
        config = "config/test-it-config.yaml",
        withDockerSQS = true
)
public class ChargesApiResourceCreateAgreementIT extends ChargingITestBase {
    private Appender<ILoggingEvent> mockAppender = mock(Appender.class);
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor = ArgumentCaptor.forClass(LoggingEvent.class);

    private static final String JSON_AGREEMENT_ID_KEY = "agreement_id";
    private static final String JSON_SAVE_PAYMENT_INSTRUMENT_TO_AGREEMENT_KEY = "save_payment_instrument_to_agreement";
    private static final String JSON_VALID_AGREEMENT_ID_VALUE = "12345678901234567890123456";
    private static final String JSON_TOO_SHORT_AGREEMENT_ID_VALUE = "12345678901234567890";
    private static final String JSON_TOO_LONG_AGREEMENT_ID_VALUE = "123456789012345678901234567890";
    private static final String JSON_AUTH_MODE_AGREEMENT = "agreement";

    public ChargesApiResourceCreateAgreementIT() {
        super(PROVIDER_NAME);
    }

    @Override
    @Before
    public void setUp() {
        super.setUp();
        Logger root = (Logger) LoggerFactory.getLogger(TaskQueue.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
    }

    @Test
    public void shouldCreatePaymentWithAgreementIdAndSavePaymentInstrumentToAgreementTrue() {
        databaseTestHelper.enableRecurring(Long.valueOf(accountId));
        AddAgreementParams agreementParams = anAddAgreementParams()
                .withGatewayAccountId(accountId)
                .withExternalAgreementId(JSON_VALID_AGREEMENT_ID_VALUE)
                .build();
        databaseTestHelper.addAgreement(agreementParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                JSON_SAVE_PAYMENT_INSTRUMENT_TO_AGREEMENT_KEY, "true"
        ));

        String chargeId = connectorRestApiClient
                .postCreateCharge(postBody)
                .statusCode(SC_CREATED)
                .body(JSON_AGREEMENT_ID_KEY, is(JSON_VALID_AGREEMENT_ID_VALUE))
                .contentType(JSON)
                .extract().path("charge_id");

        assertFrontendChargeStatusIs(chargeId, CREATED.getValue());
        assertApiStateIs(chargeId, EXTERNAL_CREATED.getStatus());
    }

    @Test
    public void shouldCreatePaymentWithAgreementIdAndAuthorisationModeAgreement() {
        databaseTestHelper.enableRecurring(Long.valueOf(accountId));
        Long paymentInstrumentId = RandomUtils.nextLong();

        AddPaymentInstrumentParams paymentInstrumentParams = anAddPaymentInstrumentParams()
                .withPaymentInstrumentId(paymentInstrumentId)
                .withPaymentInstrumentStatus(PaymentInstrumentStatus.ACTIVE).build();
        databaseTestHelper.addPaymentInstrument(paymentInstrumentParams);

        AddAgreementParams agreementParams = anAddAgreementParams()
                .withGatewayAccountId(accountId)
                .withExternalAgreementId(JSON_VALID_AGREEMENT_ID_VALUE)
                .withPaymentInstrumentId(paymentInstrumentId)
                .build();
        databaseTestHelper.addAgreement(agreementParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                JSON_AUTH_MODE_KEY, JSON_AUTH_MODE_AGREEMENT
        ));

        String chargeId = connectorRestApiClient
                .postCreateCharge(postBody)
                .statusCode(SC_CREATED)
                .body(JSON_AGREEMENT_ID_KEY, is(JSON_VALID_AGREEMENT_ID_VALUE))
                .contentType(JSON)
                .extract().path("charge_id");

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_USER_NOT_PRESENT_QUEUED.getValue());
        assertApiStateIs(chargeId, EXTERNAL_STARTED.getStatus());

        verify(mockAppender).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logEvents = loggingEventArgumentCaptor.getAllValues();

        LoggingEvent log = logEvents.get(0);
        List<String> logArguments = Arrays.stream(log.getArgumentArray()).map(String::valueOf).collect(Collectors.toUnmodifiableList());
        assertThat(log.getMessage(), is("Task added to queue"));
        assertThat(logArguments, hasItem("task_type=authorise_with_user_not_present"));
    }

    @Test
    public void shouldReturn400WhenSavePaymentInstrumentToAgreementIsTrueButNoAgreementId() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("worldpay")
                .withAccountId(accountId)
                .withRecurringEnabled(true)
                .build();
        databaseTestHelper.addGatewayAccount(gatewayAccountParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_SAVE_PAYMENT_INSTRUMENT_TO_AGREEMENT_KEY,
                "true"
        ));

        connectorRestApiClient
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_BAD_REQUEST)
                .contentType(JSON)
                .body("message", contains("If [save_payment_instrument_to_agreement] is true, [agreement_id] must be specified"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    public void shouldReturn400WhenSavePaymentInstrumentToAgreementTrueAndAgreementNotFound() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("worldpay")
                .withAccountId(accountId)
                .withRecurringEnabled(true)
                .build();
        databaseTestHelper.addGatewayAccount(gatewayAccountParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                JSON_SAVE_PAYMENT_INSTRUMENT_TO_AGREEMENT_KEY, "true"
        ));

        connectorRestApiClient
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_BAD_REQUEST)
                .contentType(JSON)
                .body("error_identifier", is(ErrorIdentifier.AGREEMENT_NOT_FOUND.toString()));
    }

    @Test
    public void shouldReturn422WhenAuthorisationModeAgreementButNoAgreementId() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("worldpay")
                .withAccountId(accountId)
                .withRecurringEnabled(true)
                .build();
        databaseTestHelper.addGatewayAccount(gatewayAccountParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_AUTH_MODE_KEY, JSON_AUTH_MODE_AGREEMENT
        ));

        connectorRestApiClient
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_UNPROCESSABLE_ENTITY)
                .contentType(JSON)
                .body("message", contains("Missing mandatory attribute: agreement_id"))
                .body("error_identifier", is(ErrorIdentifier.MISSING_MANDATORY_ATTRIBUTE.toString()));
    }

    @Test
    public void shouldReturn422WhenAuthorisationModeAgreementAndReturnUrlProvided() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("sandbox")
                .withAccountId(accountId)
                .withRecurringEnabled(true)
                .build();
        databaseTestHelper.addGatewayAccount(gatewayAccountParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_AUTH_MODE_KEY, JSON_AUTH_MODE_AGREEMENT
        ));

        connectorRestApiClient
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_UNPROCESSABLE_ENTITY)
                .contentType(JSON)
                .body("message", contains("Unexpected attribute: return_url"))
                .body("error_identifier", is(ErrorIdentifier.UNEXPECTED_ATTRIBUTE.toString()));
    }

    @Test
    public void shouldReturn422WhenAuthorisationModeAgreementAndMotoTrue() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("sandbox")
                .withAccountId(accountId)
                .withAllowMoto(true)
                .withRecurringEnabled(true)
                .build();
        databaseTestHelper.addGatewayAccount(gatewayAccountParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_AUTH_MODE_KEY, JSON_AUTH_MODE_AGREEMENT,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                JSON_MOTO_KEY, true
                ));

        connectorRestApiClient
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_UNPROCESSABLE_ENTITY)
                .contentType(JSON)
                .body("message", contains("Unexpected attribute: moto"))
                .body("error_identifier", is(ErrorIdentifier.UNEXPECTED_ATTRIBUTE.toString()));
    }

    @Test
    public void shouldReturn422WhenAuthorisationModeAgreementAndEmailProvided() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("sandbox")
                .withAccountId(accountId)
                .withRecurringEnabled(true)
                .build();
        databaseTestHelper.addGatewayAccount(gatewayAccountParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_AUTH_MODE_KEY, JSON_AUTH_MODE_AGREEMENT,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                JSON_EMAIL_KEY, "test@test.test"
        ));

        connectorRestApiClient
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_UNPROCESSABLE_ENTITY)
                .contentType(JSON)
                .body("message", contains("Unexpected attribute: email"))
                .body("error_identifier", is(ErrorIdentifier.UNEXPECTED_ATTRIBUTE.toString()));
    }

    @Test
    public void shouldReturn422WhenAuthorisationModeAgreementAndPrefilledCardholderDetailsProvided() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("sandbox")
                .withAccountId(accountId)
                .withRecurringEnabled(true)
                .build();
        databaseTestHelper.addGatewayAccount(gatewayAccountParams);

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

        connectorRestApiClient
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_UNPROCESSABLE_ENTITY)
                .contentType(JSON)
                .body("message", contains("Unexpected attribute: prefilled_cardholder_details"))
                .body("error_identifier", is(ErrorIdentifier.UNEXPECTED_ATTRIBUTE.toString()));
    }

    @Test
    public void shouldReturn400WhenAuthorisationModeIsAgreementAndAgreementNotFound() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("worldpay")
                .withAccountId(accountId)
                .withRecurringEnabled(true)
                .build();
        databaseTestHelper.addGatewayAccount(gatewayAccountParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                "authorisation_mode", "agreement"
        ));

        connectorRestApiClient
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_BAD_REQUEST)
                .body("message", contains("Agreement with ID [" + JSON_VALID_AGREEMENT_ID_VALUE + "] not found."))
                .body("error_identifier", is(ErrorIdentifier.AGREEMENT_NOT_FOUND.toString()));
    }

    @Test
    public void shouldReturn400WhenAuthorisationModeIsAgreementAndPaymentInstrumentNotFound() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("worldpay")
                .withAccountId(accountId)
                .withRecurringEnabled(true)
                .build();
        databaseTestHelper.addGatewayAccount(gatewayAccountParams);

        AddAgreementParams agreementParams = anAddAgreementParams()
                .withGatewayAccountId(accountId)
                .withExternalAgreementId(JSON_VALID_AGREEMENT_ID_VALUE)
                .build();
        databaseTestHelper.addAgreement(agreementParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                "authorisation_mode", "agreement"
        ));

        connectorRestApiClient
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_BAD_REQUEST)
                .body("message", contains("Agreement with ID [" + JSON_VALID_AGREEMENT_ID_VALUE + "] does not have a payment instrument"))
                .body("error_identifier", is(ErrorIdentifier.AGREEMENT_NOT_ACTIVE.toString()));
    }

    @Test
    public void shouldReturn400WhenAuthorisationModeIsAgreementAndPaymentInstrumentIsNotActive() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("worldpay")
                .withAccountId(accountId)
                .withRecurringEnabled(true)
                .build();
        long paymentInstrumentId = 11L;
        databaseTestHelper.addGatewayAccount(gatewayAccountParams);
        String paymentInstrumentExternalId = "this-has-status-created";
        AddPaymentInstrumentParams addPaymentInstrumentParams = anAddPaymentInstrumentParams()
                .withPaymentInstrumentId(paymentInstrumentId)
                .withExternalPaymentInstrumentId(paymentInstrumentExternalId)
                .withPaymentInstrumentStatus(PaymentInstrumentStatus.CREATED)
                .build();
        databaseTestHelper.addPaymentInstrument(addPaymentInstrumentParams);
        AddAgreementParams agreementParams = anAddAgreementParams()
                .withGatewayAccountId(accountId)
                .withExternalAgreementId(JSON_VALID_AGREEMENT_ID_VALUE)
                .withPaymentInstrumentId(paymentInstrumentId)
                .build();
        databaseTestHelper.addAgreement(agreementParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                "authorisation_mode", "agreement"
        ));

        connectorRestApiClient
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_BAD_REQUEST)
                .body("message", contains("Agreement with ID [" + JSON_VALID_AGREEMENT_ID_VALUE + "] has payment instrument with ID ["
                        + paymentInstrumentExternalId+ "] but its state is [" + PaymentInstrumentStatus.CREATED + "]"))
                .body("error_identifier", is(ErrorIdentifier.AGREEMENT_NOT_ACTIVE.toString()));

    }

    @Test
    public void shouldReturn400WhenAuthorisationModeMotoApiAndAgreementId() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("worldpay")
                .withAccountId(accountId)
                .withAllowMoto(true)
                .withAllowAuthApi(true)
                .build();
        databaseTestHelper.addGatewayAccount(gatewayAccountParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                JSON_AUTH_MODE_KEY, "moto_api"
        ));

        connectorRestApiClient
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_UNPROCESSABLE_ENTITY)
                .contentType(JSON)
                .body("message", contains("Unexpected attribute: agreement_id"))
                .body("error_identifier", is(ErrorIdentifier.UNEXPECTED_ATTRIBUTE.toString()));
    }

    @Test
    public void shouldReturn400WhenSavePaymentInstrumentToAgreementTrueAuthorisationModeAgreementAndAgreementId() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("worldpay")
                .withAccountId(accountId)
                .withRecurringEnabled(true)
                .build();
        databaseTestHelper.addGatewayAccount(gatewayAccountParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                JSON_SAVE_PAYMENT_INSTRUMENT_TO_AGREEMENT_KEY, true,
                JSON_AUTH_MODE_KEY, JSON_AUTH_MODE_AGREEMENT
        ));

        connectorRestApiClient
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_BAD_REQUEST)
                .contentType(JSON)
                .body("message", contains("If [save_payment_instrument_to_agreement] is true, [authorisation_mode] must be [web]"))
                .body("error_identifier", is(ErrorIdentifier.INCORRECT_AUTHORISATION_MODE_FOR_SAVE_PAYMENT_INSTRUMENT_TO_AGREEMENT.toString()));
    }

    @Test
    public void shouldReturn400WhenAgreementIdIsProvidedButNotSavePaymentInstrumentToAgreementNorAuthorisationModeAgreement() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("worldpay")
                .withAccountId(accountId)
                .build();
        databaseTestHelper.addGatewayAccount(gatewayAccountParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE
        ));

        connectorRestApiClient
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_UNPROCESSABLE_ENTITY)
                .contentType(JSON)
                .body("message", contains("Unexpected attribute: agreement_id"))
                .body("error_identifier", is(ErrorIdentifier.UNEXPECTED_ATTRIBUTE.toString()));
    }

    @Test
    public void shouldReturn400WhenAgreementIdIsProvidedButSavePaymentInstrumentToAgreementFalse() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("worldpay")
                .withAccountId(accountId)
                .build();
        databaseTestHelper.addGatewayAccount(gatewayAccountParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                JSON_SAVE_PAYMENT_INSTRUMENT_TO_AGREEMENT_KEY, false
        ));

        connectorRestApiClient
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_UNPROCESSABLE_ENTITY)
                .contentType(JSON)
                .body("message", contains("Unexpected attribute: agreement_id"))
                .body("error_identifier", is(ErrorIdentifier.UNEXPECTED_ATTRIBUTE.toString()));
    }

    @Test
    public void shouldReturn422WhenAgreementIdIdExceed26Characters() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("worldpay")
                .withAccountId(accountId)
                .build();
        databaseTestHelper.addGatewayAccount(gatewayAccountParams);
        AddAgreementParams agreementParams = anAddAgreementParams()
                .withGatewayAccountId(accountId)
                .withExternalAgreementId(JSON_TOO_LONG_AGREEMENT_ID_VALUE)
                .build();
        databaseTestHelper.addAgreement(agreementParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_AGREEMENT_ID_KEY, JSON_TOO_LONG_AGREEMENT_ID_VALUE,
                JSON_SAVE_PAYMENT_INSTRUMENT_TO_AGREEMENT_KEY,
                "true"
        ));

        connectorRestApiClient
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_UNPROCESSABLE_ENTITY)
                .contentType(JSON);
    }

    @Test
    public void shouldReturn422WhenAgreementIdIdFewerThan26Characters() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("worldpay")
                .withAccountId(accountId)
                .build();
        databaseTestHelper.addGatewayAccount(gatewayAccountParams);
        AddAgreementParams agreementParams = anAddAgreementParams()
                .withGatewayAccountId(accountId)
                .withExternalAgreementId(JSON_TOO_SHORT_AGREEMENT_ID_VALUE)
                .build();
        databaseTestHelper.addAgreement(agreementParams);
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_AGREEMENT_ID_KEY, JSON_TOO_SHORT_AGREEMENT_ID_VALUE,
                JSON_SAVE_PAYMENT_INSTRUMENT_TO_AGREEMENT_KEY,
                "true"
        ));

        connectorRestApiClient
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_UNPROCESSABLE_ENTITY)
                .contentType(JSON);
    }
    
    @Test
    public void shouldReturn422OnSavePaymentToInstrumentRequestWhenRecurringNotEnabledForGatewayAccount() {
        AddAgreementParams agreementParams = anAddAgreementParams()
                .withGatewayAccountId(accountId)
                .withExternalAgreementId(JSON_VALID_AGREEMENT_ID_VALUE)
                .build();
        databaseTestHelper.addAgreement(agreementParams);
        
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                JSON_SAVE_PAYMENT_INSTRUMENT_TO_AGREEMENT_KEY, "true"
                ));
        
        connectorRestApiClient
                .postCreateCharge(postBody)
                .statusCode(SC_UNPROCESSABLE_ENTITY)
                .contentType(JSON)
                .body("message", contains("Recurring payment agreements are not enabled on this account"))
                .body("error_identifier", is(ErrorIdentifier.RECURRING_CARD_PAYMENTS_NOT_ALLOWED.toString()));
        }

    @Test
    public void shouldReturn422OnRecurringPaymentRequestWhenRecurringNotEnabledForGatewayAccount() {
        Long paymentInstrumentId = RandomUtils.nextLong();

        AddPaymentInstrumentParams paymentInstrumentParams = anAddPaymentInstrumentParams()
                .withPaymentInstrumentId(paymentInstrumentId)
                .withPaymentInstrumentStatus(PaymentInstrumentStatus.ACTIVE).build();
        databaseTestHelper.addPaymentInstrument(paymentInstrumentParams);

        AddAgreementParams agreementParams = anAddAgreementParams()
                .withGatewayAccountId(accountId)
                .withExternalAgreementId(JSON_VALID_AGREEMENT_ID_VALUE)
                .withPaymentInstrumentId(paymentInstrumentId)
                .build();
        databaseTestHelper.addAgreement(agreementParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                JSON_AUTH_MODE_KEY, JSON_AUTH_MODE_AGREEMENT
        ));

        connectorRestApiClient
                .postCreateCharge(postBody)
                .statusCode(SC_UNPROCESSABLE_ENTITY)
                .contentType(JSON)
                .body("message", contains("Recurring payment agreements are not enabled on this account"))
                .body("error_identifier", is(ErrorIdentifier.RECURRING_CARD_PAYMENTS_NOT_ALLOWED.toString()));
    }
        
    @After
    public void tearDown() {
        databaseTestHelper.truncateAllData();
    }

}
