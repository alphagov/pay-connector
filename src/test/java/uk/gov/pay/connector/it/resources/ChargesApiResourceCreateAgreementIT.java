package uk.gov.pay.connector.it.resources;

import org.apache.commons.lang.math.RandomUtils;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;
import uk.gov.pay.connector.util.AddAgreementParams;
import uk.gov.pay.connector.util.AddGatewayAccountParams;
import uk.gov.pay.connector.util.AddPaymentInstrumentParams;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.util.Map;

import static io.restassured.http.ContentType.JSON;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.util.AddAgreementParams.AddAgreementParamsBuilder.anAddAgreementParams;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;
import static uk.gov.pay.connector.util.AddPaymentInstrumentParams.AddPaymentInstrumentParamsBuilder.anAddPaymentInstrumentParams;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class ChargesApiResourceCreateAgreementIT extends ChargingITestBase {

    private static final String JSON_AGREEMENT_ID_KEY = "agreement_id";
    private static final String JSON_SAVE_PAYMENT_INSTRUMENT_TO_AGREEMENT_KEY = "save_payment_instrument_to_agreement";
    private static final String JSON_VALID_AGREEMENT_ID_VALUE = "12345678901234567890123456";
    private static final String JSON_TOO_SHORT_AGREEMENT_ID_VALUE = "12345678901234567890";
    private static final String JSON_TOO_LONG_AGREEMENT_ID_VALUE = "123456789012345678901234567890";
    private static final String JSON_AUTH_MODE_AGREEMENT = "agreement";

    public ChargesApiResourceCreateAgreementIT() {
        super(PROVIDER_NAME);
    }

    @Test
    public void shouldCreatePaymentWithAgreementIdAndSavePaymentInstrumentToAgreementTrue() {
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
                .statusCode(SC_CREATED)
                .body(JSON_AGREEMENT_ID_KEY, is(JSON_VALID_AGREEMENT_ID_VALUE))
                .contentType(JSON);
    }

    @Test
    public void shouldCreatePaymentWithAgreementIdAndAuthorisationModeAgreement() {
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
                .statusCode(SC_CREATED)
                .body(JSON_AGREEMENT_ID_KEY, is(JSON_VALID_AGREEMENT_ID_VALUE))
                .contentType(JSON);
    }

    @Test
    public void shouldReturn400WhenSavePaymentInstrumentToAgreementIsTrueButNoAgreementId() {
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
    public void shouldReturn400WhenAuthorisationModeAgreementButNoAgreementId() {
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
                JSON_AUTH_MODE_KEY, JSON_AUTH_MODE_AGREEMENT
        ));

        connectorRestApiClient
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_BAD_REQUEST)
                .contentType(JSON)
                .body("message", contains("If [authorisation_mode] is [agreement], [agreement_id] must be specified"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    public void shouldReturn422WhenAuthorisationModeAgreementAndReturnUrlProvided() {
        String accountId = String.valueOf(RandomUtils.nextInt());
        AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
                .withPaymentGateway("sandbox")
                .withAccountId(accountId)
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
    public void shouldReturn400WhenAuthorisationModeIsAgreementAndAgreementNotFound() {
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
                .statusCode(SC_BAD_REQUEST)
                .contentType(JSON)
                .body("message", contains("If [agreement_id] is present, [authorisation_mode] must be [agreement] or [web]"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    public void shouldReturn400WhenSavePaymentInstrumentToAgreementTrueAuthorisationModeAgreementAndAgreementId() {
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
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                JSON_SAVE_PAYMENT_INSTRUMENT_TO_AGREEMENT_KEY, true,
                JSON_AUTH_MODE_KEY, JSON_AUTH_MODE_AGREEMENT
        ));

        connectorRestApiClient
                .postCreateCharge(postBody, accountId)
                .statusCode(SC_BAD_REQUEST)
                .contentType(JSON)
                .body("message", contains("If [save_payment_instrument_to_agreement] is true, [authorisation_mode] must be [web]"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
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
                .statusCode(SC_BAD_REQUEST)
                .contentType(JSON)
                .body("message", contains("If [agreement_id] is present, either [save_payment_instrument_to_agreement] must be true " +
                        "or [authorisation_mode] must be [agreement]"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
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
                .statusCode(SC_BAD_REQUEST)
                .contentType(JSON)
                .body("message", contains("If [agreement_id] is present, either [save_payment_instrument_to_agreement] must be true " +
                        "or [authorisation_mode] must be [agreement]"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
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

    @After
    public void tearDown() {
        databaseTestHelper.truncateAllData();
    }

}
