package uk.gov.pay.connector.it.resources.worldpay;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.it.util.ChargeUtils;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingXPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.HttpHeaders.COOKIE;
import static javax.ws.rs.core.Response.Status.OK;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.PAYMENT_REQUIRED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_SUCCESS;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse.WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse.WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildCorporateJsonAuthorisationDetailsFor;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonApplePayAuthorisationDetails;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonAuthorisationDetailsFor;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonAuthorisationDetailsWithoutAddress;
import static uk.gov.pay.connector.rules.WorldpayMockClient.WORLDPAY_URL;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(
        app = ConnectorApp.class,
        config = "config/test-it-config.yaml",
        withDockerSQS = true
)
public class WorldpayCardResourceIT extends ChargingITestBase {

    private String validAuthorisationDetails = buildJsonAuthorisationDetailsFor("4444333322221111", "visa");
    private String validApplePayAuthorisationDetails = buildJsonApplePayAuthorisationDetails("mr payment", "mr@payment.test");
    private final ObjectMapper mapper = new ObjectMapper();
    
    public WorldpayCardResourceIT() {
        super("worldpay");
    }

    @Test
    public void should_set_new_gateway_transaction_id_when_authorisation_with_exemption_engine_results_in_soft_decline() {
        String gatewayTransactionId = randomUUID().toString();
        String chargeId = createNewChargeWith(ENTERING_CARD_DETAILS, gatewayTransactionId);

        assertEquals(databaseTestHelper.getChargeByExternalId(chargeId).get("gateway_transaction_id"), gatewayTransactionId);

        worldpayMockClient.mockResponsesForExemptionEngineSoftDecline();

        givenSetup()
                .body(validAuthorisationDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .statusCode(OK.getStatusCode());

        assertNotEquals(databaseTestHelper.getChargeByExternalId(chargeId), gatewayTransactionId);
    }

    @Test
    public void shouldAuthoriseChargeAndCreatePaymentInstrumentWhenTokenInWorldpayResponse() throws JsonProcessingException {

        ChargeUtils.ExternalChargeId externalChargeId = addChargeForSetUpAgreement(ENTERING_CARD_DETAILS);

        worldpayMockClient.mockAuthorisationSuccessWithRecurringPaymentToken();
        
        givenSetup()
                .body(validAuthorisationDetails)
                .post(authoriseChargeUrlFor(externalChargeId.toString()))
                .then()
                .body("status", is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(200);
        
        Map<String, Object> paymentInstrument = databaseTestHelper.getPaymentInstrumentByChargeExternalId(externalChargeId.toString());
        Map<String, String> recurringAuthTokenMap = mapper.readValue(paymentInstrument.get("recurring_auth_token").toString(), new TypeReference<Map<String, String>>() {});
        
        assertThat(recurringAuthTokenMap, hasEntry(WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY, "9961191959944156907"));
        assertThat(recurringAuthTokenMap, hasEntry(WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY, "1234567890"));
        assertFrontendChargeStatusIs(externalChargeId.toString(), AUTHORISATION_SUCCESS.toString());
    }

    @Test
    public void shouldAuthoriseChargeAndCreatePaymentInstrumentWhenTokenInWorldpayResponse_for3dsRequiredCharge() throws JsonProcessingException {

        ChargeUtils.ExternalChargeId externalChargeId = addChargeForSetUpAgreement(AUTHORISATION_3DS_REQUIRED);

        worldpayMockClient.mockAuthorisationSuccessWithRecurringPaymentToken();

        givenSetup()
                .body(buildJsonWithPaResponse())
                .post(authorise3dsChargeUrlFor(externalChargeId.toString()))
                .then()
                .statusCode(200);

        Map<String, Object> paymentInstrument = databaseTestHelper.getPaymentInstrumentByChargeExternalId(externalChargeId.toString());
        Map<String, String> recurringAuthTokenMap = mapper.readValue(paymentInstrument.get("recurring_auth_token").toString(), new TypeReference<Map<String, String>>() {});

        assertThat(recurringAuthTokenMap, hasEntry(WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY, "9961191959944156907"));
        assertThat(recurringAuthTokenMap, hasEntry(WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY, "1234567890"));
        assertFrontendChargeStatusIs(externalChargeId.toString(), AUTHORISATION_SUCCESS.toString());
    }

    @Test
    public void shouldAuthoriseChargeWithoutCorporateCard_ForValidAuthorisationDetails() {

        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        worldpayMockClient.mockAuthorisationSuccess();

        givenSetup()
                .body(validAuthorisationDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .body("status", is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.toString());
    }

    @Test
    public void shouldAuthoriseChargeWithApplePay_ForValidAuthorisationDetails() {

        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        worldpayMockClient.mockAuthorisationSuccess();

        givenSetup()
                .body(validApplePayAuthorisationDetails)
                .post(authoriseChargeUrlForApplePay(chargeId))
                .then()
                .body("status", is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.toString());
    }

    @Test
    public void shouldNotAuthoriseChargeWithApplePay_ForAWorldpayErrorCard() {

        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        worldpayMockClient.mockAuthorisationFailure();

        givenSetup()
                .body(validApplePayAuthorisationDetails)
                .post(authoriseChargeUrlForApplePay(chargeId))
                .then()
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", contains("This transaction was declined."))
                .body("error_identifier", is(ErrorIdentifier.AUTHORISATION_REJECTED.toString()));

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_REJECTED.getValue());
    }

    @Test
    public void shouldAuthoriseChargeWithCorporateCard_ForValidAuthorisationDetails() {

        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        worldpayMockClient.mockAuthorisationSuccess();

        String corporateCreditAuthDetails = buildCorporateJsonAuthorisationDetailsFor(PayersCardType.CREDIT);

        givenSetup()
                .body(corporateCreditAuthDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .body("status", is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.toString());
    }

    @Test
    public void shouldAuthoriseChargeWithoutBillingAddress() {

        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        worldpayMockClient.mockAuthorisationSuccess();

        String authDetails = buildJsonAuthorisationDetailsWithoutAddress();

        givenSetup()
                .body(authDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .body("status", is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.toString());

        verifyRequestBodyToWorldpay(WORLDPAY_URL);
    }

    private void verifyRequestBodyToWorldpay(String path) {
        wireMockServer.verify(
                postRequestedFor(urlPathEqualTo(path))
                        .withHeader("Content-Type", equalTo("application/xml"))
                        .withHeader("Authorization", equalTo("Basic dGVzdC11c2VyOnRlc3QtcGFzc3dvcmQ="))
                        .withRequestBody(matchingXPath(getMatchingXPath("paymentService", "merchantCode", "merchant-id")))
                        .withRequestBody(matchingXPath(getMatchingXPathForText("description", "Test description")))
                        .withRequestBody(matchingXPath(getMatchingXPath("amount", "value", "6234")))
                        .withRequestBody(matchingXPath(getMatchingXPath("amount", "currencyCode", "GBP")))
                        .withRequestBody(matchingXPath(getMatchingXPathForText("cardHolderName", "Scrooge McDuck")))
                        .withRequestBody(matchingXPath(getMatchingXPathForText("cardNumber", "4242424242424242")))
                        .withRequestBody(matchingXPath(getMatchingXPath("date", "month", "11")))
                        .withRequestBody(matchingXPath(getMatchingXPath("date", "year", "2099")))
                        .withRequestBody(matchingXPath(getMatchingXPathForText("cvc", "123")))
        );
    }

    public String getMatchingXPath(String path, String attribute, String value) {
        return format("//%s[@%s=\"%s\"]", path, attribute, value);
    }

    public String getMatchingXPathForText(String path, String value) {
        return format("//%s[text()=\"%s\"]", path, value);
    }

    @Test
    public void shouldReturnStatusAsRequires3ds() {
        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        worldpayMockClient.mockAuthorisationRequires3ds();

        givenSetup()
                .body(validAuthorisationDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .body("status", is(AUTHORISATION_3DS_REQUIRED.toString()))
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_3DS_REQUIRED.toString());
    }

    @Test
    public void shouldNotAuthorise_AWorldpayErrorCard() {
        String cardDetailsRejectedByWorldpay = buildJsonAuthorisationDetailsFor("REFUSED", "4444333322221111", "visa");

        worldpayMockClient.mockAuthorisationFailure();

        String expectedErrorMessage = "This transaction was declined.";
        String expectedChargeStatus = AUTHORISATION_REJECTED.getValue();
        shouldReturnErrorForAuthorisationDetailsWithMessage(cardDetailsRejectedByWorldpay, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    public void shouldPersistTransactionIdWhenAuthorisationException() {
        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        worldpayMockClient.mockAuthorisationGatewayError();

        givenSetup()
                .body(validAuthorisationDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .statusCode(PAYMENT_REQUIRED.getStatusCode());

        assertFrontendChargeStatusAndTransactionId(chargeId, AUTHORISATION_UNEXPECTED_ERROR.toString());
    }

    @Test
    public void shouldDeferCaptureCardPayment_IfAsynchronousFeatureFlagIsOn() {
        String chargeId = authoriseNewCharge();

        worldpayMockClient.mockCaptureSuccess();

        givenSetup()
                .post(captureChargeUrlFor(chargeId))
                .then()
                .statusCode(204);

        assertApiStateIs(chargeId, EXTERNAL_SUCCESS.getStatus());
    }

    @Test
    public void shouldAuthoriseCharge_For3dsRequiredCharge() {
        String chargeId = createNewCharge(AUTHORISATION_3DS_REQUIRED);
        worldpayMockClient.mockAuthorisationSuccess();

        givenSetup()
                .body(buildJsonWithPaResponse())
                .post(authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());
    }

    @Test
    public void shouldReturnStatus400_WhenAuthorisationFails() {
        String chargeId = createNewCharge(AUTHORISATION_3DS_REQUIRED);

        worldpayMockClient.mockAuthorisationFailure();

        givenSetup()
                .body(buildJsonWithPaResponse())
                .post(authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("status", is(AUTHORISATION_REJECTED.toString()));

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_REJECTED.getValue());
    }

    @Test
    public void shouldReturnStatus402_WhenAuthorisationCallThrowsException() {
        String chargeId = createNewCharge(AUTHORISATION_3DS_REQUIRED);

        String expectedErrorMessage = "There was an error when attempting to authorise the transaction.";
        worldpayMockClient.mockServerFault();

        givenSetup()
                .body(buildJsonWithPaResponse())
                .post(authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(PAYMENT_REQUIRED.getStatusCode())
                .contentType(JSON)
                .body("message", contains(expectedErrorMessage))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_ERROR.getValue());
    }

    @Test
    public void shouldReturnStatus402_AWorldpayPaResParseError() {
        String chargeId = createNewCharge(AUTHORISATION_3DS_REQUIRED);

        String expectedErrorMessage = "There was an error when attempting to authorise the transaction.";
        worldpayMockClient.mockAuthorisationPaResParseError();

        givenSetup()
                .body(buildJsonWithPaResponse())
                .post(authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(PAYMENT_REQUIRED.getStatusCode())
                .contentType(JSON)
                .body("message", contains(expectedErrorMessage))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_ERROR.getValue());
    }

    @Test
    public void shouldIncludeMachineCookieInTheRequestHeaderInThe3dsAuthorisationRequestToWorldpay() {
        String machineCookie = "0ab20016";
        String chargeExternalId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        worldpayMockClient.mockAuthorisationRequires3dsWithMachineCookie(machineCookie);

        givenSetup()
                .body(validAuthorisationDetails)
                .post(authoriseChargeUrlFor(chargeExternalId))
                .then()
                .body("status", is(AUTHORISATION_3DS_REQUIRED.toString()))
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeExternalId, AUTHORISATION_3DS_REQUIRED.toString());

        Map<String, Object> charge = databaseTestHelper.getChargeByExternalId(chargeExternalId);

        assertThat(charge.get("provider_session_id").toString(), is(machineCookie));

        worldpayMockClient.mockAuthorisationSuccess3dsMatchingOnMachineCookie(machineCookie);

        givenSetup()
                .body(buildJsonWithPaResponse())
                .post(authorise3dsChargeUrlFor(chargeExternalId))
                .then()
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeExternalId, AUTHORISATION_SUCCESS.getValue());

        wireMockServer.verify(
                postRequestedFor(urlPathEqualTo(WORLDPAY_URL))
                        .withHeader("Content-Type", equalTo("application/xml"))
                        .withHeader("Authorization", equalTo("Basic dGVzdC11c2VyOnRlc3QtcGFzc3dvcmQ="))
                        .withHeader(COOKIE, matching("machine=" + machineCookie))
                        .withRequestBody(matchingXPath(getMatchingXPath("paymentService", "merchantCode", "merchant-id")))
                        .withRequestBody(matchingXPath(getMatchingXPath("order", "orderCode", "ExampleOrder1")))
                        .withRequestBody(matchingXPath(getMatchingXPathForText("paResponse", "this-is-a-test-pa-response")))
                        .withRequestBody(matchingXPath(getMatchingXPath("session", "id", "charge-" + charge.get("id"))))
        );
    }
}
