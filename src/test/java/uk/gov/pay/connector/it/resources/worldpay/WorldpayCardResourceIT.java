package uk.gov.pay.connector.it.resources.worldpay;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.it.util.ChargeUtils;
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
import static jakarta.ws.rs.core.HttpHeaders.COOKIE;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.Status.PAYMENT_REQUIRED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

public class WorldpayCardResourceIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("worldpay", app.getLocalPort(), app.getDatabaseTestHelper());

    private String validAuthorisationDetails = buildJsonAuthorisationDetailsFor("4444333322221111", "visa");
    private String validApplePayAuthorisationDetails = buildJsonApplePayAuthorisationDetails("mr payment", "mr@payment.test");
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Test
    void should_set_new_gateway_transaction_id_when_authorisation_with_exemption_engine_results_in_soft_decline() {
        String gatewayTransactionId = randomUUID().toString();
        String chargeId = testBaseExtension.createNewChargeWith(ENTERING_CARD_DETAILS, gatewayTransactionId);

        assertEquals(app.getDatabaseTestHelper().getChargeByExternalId(chargeId).get("gateway_transaction_id"), gatewayTransactionId);

        app.getWorldpayMockClient().mockResponsesForExemptionEngineSoftDecline();

        app.givenSetup()
                .body(validAuthorisationDetails)
                .post(ITestBaseExtension.authoriseChargeUrlFor(chargeId))
                .then()
                .statusCode(OK.getStatusCode());

        assertNotEquals(app.getDatabaseTestHelper().getChargeByExternalId(chargeId), gatewayTransactionId);
    }

    @Test
    void shouldAuthoriseChargeAndCreatePaymentInstrumentWhenTokenInWorldpayResponse() throws JsonProcessingException {

        ChargeUtils.ExternalChargeId externalChargeId = testBaseExtension.addChargeForSetUpAgreement(ENTERING_CARD_DETAILS);

        app.getWorldpayMockClient().mockAuthorisationSuccessWithRecurringPaymentToken();
        
        app.givenSetup()
                .body(validAuthorisationDetails)
                .post(ITestBaseExtension.authoriseChargeUrlFor(externalChargeId.toString()))
                .then()
                .body("status", is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(200);
        
        Map<String, Object> paymentInstrument = app.getDatabaseTestHelper().getPaymentInstrumentByChargeExternalId(externalChargeId.toString());
        Map<String, String> recurringAuthTokenMap = mapper.readValue(paymentInstrument.get("recurring_auth_token").toString(), new TypeReference<Map<String, String>>() {});
        
        assertThat(recurringAuthTokenMap, hasEntry(WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY, "9961191959944156907"));
        assertThat(recurringAuthTokenMap, hasEntry(WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY, "1234567890"));
        testBaseExtension.assertFrontendChargeStatusIs(externalChargeId.toString(), AUTHORISATION_SUCCESS.toString());
    }

    @Test
    void shouldAuthoriseChargeAndCreatePaymentInstrumentWhenTokenInWorldpayResponse_for3dsRequiredCharge() throws JsonProcessingException {

        ChargeUtils.ExternalChargeId externalChargeId = testBaseExtension.addChargeForSetUpAgreement(AUTHORISATION_3DS_REQUIRED);

        app.getWorldpayMockClient().mockAuthorisationSuccessWithRecurringPaymentToken();

        app.givenSetup()
                .body(ITestBaseExtension.buildJsonWithPaResponse())
                .post(ITestBaseExtension.authorise3dsChargeUrlFor(externalChargeId.toString()))
                .then()
                .statusCode(200);

        Map<String, Object> paymentInstrument = app.getDatabaseTestHelper().getPaymentInstrumentByChargeExternalId(externalChargeId.toString());
        Map<String, String> recurringAuthTokenMap = mapper.readValue(paymentInstrument.get("recurring_auth_token").toString(), new TypeReference<Map<String, String>>() {});

        assertThat(recurringAuthTokenMap, hasEntry(WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY, "9961191959944156907"));
        assertThat(recurringAuthTokenMap, hasEntry(WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY, "1234567890"));
        testBaseExtension.assertFrontendChargeStatusIs(externalChargeId.toString(), AUTHORISATION_SUCCESS.toString());
    }

    @Test
    void shouldAuthoriseChargeWithoutCorporateCard_ForValidAuthorisationDetails() {

        String chargeId = testBaseExtension.createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        app.getWorldpayMockClient().mockAuthorisationSuccess();

        app.givenSetup()
                .body(validAuthorisationDetails)
                .post(ITestBaseExtension.authoriseChargeUrlFor(chargeId))
                .then()
                .body("status", is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(200);

        testBaseExtension.assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.toString());
    }

    @Test
    void shouldAuthoriseChargeWithApplePay_ForValidAuthorisationDetails() {

        String chargeId = testBaseExtension.createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        app.getWorldpayMockClient().mockAuthorisationSuccess();

        app.givenSetup()
                .body(validApplePayAuthorisationDetails)
                .post(ITestBaseExtension.authoriseChargeUrlForApplePay(chargeId))
                .then()
                .body("status", is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(200);

        testBaseExtension.assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.toString());
    }

    @Test
    void shouldNotAuthoriseChargeWithApplePay_ForAWorldpayErrorCard() {

        String chargeId = testBaseExtension.createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        app.getWorldpayMockClient().mockAuthorisationFailure();

        app.givenSetup()
                .body(validApplePayAuthorisationDetails)
                .post(ITestBaseExtension.authoriseChargeUrlForApplePay(chargeId))
                .then()
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", contains("This transaction was declined."))
                .body("error_identifier", is(ErrorIdentifier.AUTHORISATION_REJECTED.toString()));

        testBaseExtension.assertFrontendChargeStatusIs(chargeId, AUTHORISATION_REJECTED.getValue());
    }

    @Test
    void shouldAuthoriseChargeWithCorporateCard_ForValidAuthorisationDetails() {

        String chargeId = testBaseExtension.createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        app.getWorldpayMockClient().mockAuthorisationSuccess();

        String corporateCreditAuthDetails = buildCorporateJsonAuthorisationDetailsFor(PayersCardType.CREDIT);

        app.givenSetup()
                .body(corporateCreditAuthDetails)
                .post(ITestBaseExtension.authoriseChargeUrlFor(chargeId))
                .then()
                .body("status", is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(200);

        testBaseExtension.assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.toString());
    }

    @Test
    void shouldAuthoriseChargeWithoutBillingAddress() {

        String chargeId = testBaseExtension.createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        app.getWorldpayMockClient().mockAuthorisationSuccess();

        String authDetails = buildJsonAuthorisationDetailsWithoutAddress();

        app.givenSetup()
                .body(authDetails)
                .post(ITestBaseExtension.authoriseChargeUrlFor(chargeId))
                .then()
                .body("status", is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(200);

        testBaseExtension.assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.toString());

        verifyRequestBodyToWorldpay(WORLDPAY_URL);
    }

    private void verifyRequestBodyToWorldpay(String path) {
        app.getWorldpayWireMockServer().verify(
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
    void shouldReturnStatusAsRequires3ds() {
        String chargeId = testBaseExtension.createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        app.getWorldpayMockClient().mockAuthorisationRequires3ds();

        app.givenSetup()
                .body(validAuthorisationDetails)
                .post(ITestBaseExtension.authoriseChargeUrlFor(chargeId))
                .then()
                .body("status", is(AUTHORISATION_3DS_REQUIRED.toString()))
                .statusCode(200);

        testBaseExtension.assertFrontendChargeStatusIs(chargeId, AUTHORISATION_3DS_REQUIRED.toString());
    }

    @Test
    void shouldNotAuthorise_AWorldpayErrorCard() {
        String cardDetailsRejectedByWorldpay = buildJsonAuthorisationDetailsFor("REFUSED", "4444333322221111", "visa");

        app.getWorldpayMockClient().mockAuthorisationFailure();

        String expectedErrorMessage = "This transaction was declined.";
        String expectedChargeStatus = AUTHORISATION_REJECTED.getValue();
        testBaseExtension.shouldReturnErrorForAuthorisationDetailsWithMessage(cardDetailsRejectedByWorldpay, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    void shouldPersistTransactionIdWhenAuthorisationException() {
        String chargeId = testBaseExtension.createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        app.getWorldpayMockClient().mockAuthorisationGatewayError();

        app.givenSetup()
                .body(validAuthorisationDetails)
                .post(ITestBaseExtension.authoriseChargeUrlFor(chargeId))
                .then()
                .statusCode(PAYMENT_REQUIRED.getStatusCode());

        testBaseExtension.assertFrontendChargeStatusAndTransactionId(chargeId, AUTHORISATION_UNEXPECTED_ERROR.toString());
    }

    @Test
    void shouldDeferCaptureCardPayment_IfAsynchronousFeatureFlagIsOn() {
        String chargeId = testBaseExtension.authoriseNewCharge();

        app.getWorldpayMockClient().mockCaptureSuccess();

        app.givenSetup()
                .post(ITestBaseExtension.captureChargeUrlFor(chargeId))
                .then()
                .statusCode(204);

        testBaseExtension.assertApiStateIs(chargeId, EXTERNAL_SUCCESS.getStatus());
    }

    @Test
    void shouldAuthoriseCharge_For3dsRequiredCharge() {
        String chargeId = testBaseExtension.createNewCharge(AUTHORISATION_3DS_REQUIRED);
        app.getWorldpayMockClient().mockAuthorisationSuccess();

        app.givenSetup()
                .body(ITestBaseExtension.buildJsonWithPaResponse())
                .post(ITestBaseExtension.authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(200);

        testBaseExtension.assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());
    }

    @Test
    void shouldReturnStatus400_WhenAuthorisationFails() {
        String chargeId = testBaseExtension.createNewCharge(AUTHORISATION_3DS_REQUIRED);

        app.getWorldpayMockClient().mockAuthorisationFailure();

        app.givenSetup()
                .body(ITestBaseExtension.buildJsonWithPaResponse())
                .post(ITestBaseExtension.authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("status", is(AUTHORISATION_REJECTED.toString()));

        testBaseExtension.assertFrontendChargeStatusIs(chargeId, AUTHORISATION_REJECTED.getValue());
    }

    @Test
    void shouldReturnStatus402_WhenAuthorisationCallThrowsException() {
        String chargeId = testBaseExtension.createNewCharge(AUTHORISATION_3DS_REQUIRED);

        String expectedErrorMessage = "There was an error when attempting to authorise the transaction.";
        app.getWorldpayMockClient().mockServerFault();

        app.givenSetup()
                .body(ITestBaseExtension.buildJsonWithPaResponse())
                .post(ITestBaseExtension.authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(PAYMENT_REQUIRED.getStatusCode())
                .contentType(JSON)
                .body("message", contains(expectedErrorMessage))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));

        testBaseExtension.assertFrontendChargeStatusIs(chargeId, AUTHORISATION_ERROR.getValue());
    }

    @Test
    void shouldReturnStatus402_AWorldpayPaResParseError() {
        String chargeId = testBaseExtension.createNewCharge(AUTHORISATION_3DS_REQUIRED);

        String expectedErrorMessage = "There was an error when attempting to authorise the transaction.";
        app.getWorldpayMockClient().mockAuthorisationPaResParseError();

        app.givenSetup()
                .body(ITestBaseExtension.buildJsonWithPaResponse())
                .post(ITestBaseExtension.authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(PAYMENT_REQUIRED.getStatusCode())
                .contentType(JSON)
                .body("message", contains(expectedErrorMessage))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));

        testBaseExtension.assertFrontendChargeStatusIs(chargeId, AUTHORISATION_ERROR.getValue());
    }

    @Test
    void shouldIncludeMachineCookieInTheRequestHeaderInThe3dsAuthorisationRequestToWorldpay() {
        String machineCookie = "0ab20016";
        String chargeExternalId = testBaseExtension.createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        app.getWorldpayMockClient().mockAuthorisationRequires3dsWithMachineCookie(machineCookie);

        app.givenSetup()
                .body(validAuthorisationDetails)
                .post(ITestBaseExtension.authoriseChargeUrlFor(chargeExternalId))
                .then()
                .body("status", is(AUTHORISATION_3DS_REQUIRED.toString()))
                .statusCode(200);

        testBaseExtension.assertFrontendChargeStatusIs(chargeExternalId, AUTHORISATION_3DS_REQUIRED.toString());

        Map<String, Object> charge = app.getDatabaseTestHelper().getChargeByExternalId(chargeExternalId);

        assertThat(charge.get("provider_session_id").toString(), is(machineCookie));

        app.getWorldpayMockClient().mockAuthorisationSuccess3dsMatchingOnMachineCookie(machineCookie);

        app.givenSetup()
                .body(ITestBaseExtension.buildJsonWithPaResponse())
                .post(ITestBaseExtension.authorise3dsChargeUrlFor(chargeExternalId))
                .then()
                .statusCode(200);

        testBaseExtension.assertFrontendChargeStatusIs(chargeExternalId, AUTHORISATION_SUCCESS.getValue());

        app.getWorldpayWireMockServer().verify(
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
