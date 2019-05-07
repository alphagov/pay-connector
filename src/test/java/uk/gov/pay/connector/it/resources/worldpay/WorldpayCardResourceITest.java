package uk.gov.pay.connector.it.resources.worldpay;

import com.amazonaws.util.json.Jackson;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.commons.model.ErrorIdentifier;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import java.io.IOException;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static io.restassured.http.ContentType.JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_SUCCESS;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildCorporateJsonAuthorisationDetailsFor;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonApplePayAuthorisationDetails;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonAuthorisationDetailsFor;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonAuthorisationDetailsWithoutAddress;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class WorldpayCardResourceITest extends ChargingITestBase {

    private String validAuthorisationDetails = buildJsonAuthorisationDetailsFor("4444333322221111", "visa");
    private String validApplePayAuthorisationDetails = buildJsonApplePayAuthorisationDetails("mr payment", "mr@payment.test");

    public WorldpayCardResourceITest() {
        super("worldpay");
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
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_REJECTED.getValue());
    }

    @Test
    public void shouldAuthoriseChargeWithGooglePay() throws IOException {
        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        JsonNode validPayload = Jackson.getObjectMapper().readTree(
                fixture("googlepay/example-auth-request.json"));

        worldpayMockClient.mockAuthorisationSuccess();

        givenSetup()
                .body(validPayload)
                .post(authoriseChargeUrlForGooglePay(chargeId))
                .then()
                .statusCode(OK.getStatusCode())
                .body("status", is(ChargeStatus.AUTHORISATION_SUCCESS.toString()));
    }

    @Test
    public void shouldNotAuthoriseChargeWithGooglePay_ForAWorldpayErrorCard() throws IOException {
        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        JsonNode validPayload = Jackson.getObjectMapper().readTree(
                fixture("googlepay/example-auth-request.json"));
        worldpayMockClient.mockAuthorisationFailure();

        givenSetup()
                .body(validPayload)
                .post(authoriseChargeUrlForGooglePay(chargeId))
                .then()
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", contains("This transaction was declined."))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_REJECTED.getValue());
    }

    @Test
    public void shouldNotAuthoriseChargeForInvalidGooglePayRequest() throws IOException {
        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        JsonNode invalidPayload = Jackson.getObjectMapper().readTree(
                fixture("googlepay/invalid-empty-signature-auth-request.json"));

        givenSetup()
                .body(invalidPayload)
                .post(authoriseChargeUrlForGooglePay(chargeId))
                .then()
                .statusCode(422)
                .body("message", contains("Field [signature] must not be empty"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
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
                .statusCode(INTERNAL_SERVER_ERROR.getStatusCode());

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

        assertFrontendChargeStatusIs(chargeId, CAPTURE_APPROVED.getValue());
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

        String expectedErrorMessage = "This transaction was declined.";
        worldpayMockClient.mockAuthorisationFailure();

        givenSetup()
                .body(buildJsonWithPaResponse())
                .post(authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", contains(expectedErrorMessage))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_REJECTED.getValue());
    }

    @Test
    public void shouldReturnStatus500_WhenAuthorisationCallThrowsException() {
        String chargeId = createNewCharge(AUTHORISATION_3DS_REQUIRED);

        String expectedErrorMessage = "Failed";
        worldpayMockClient.mockServerFault();

        givenSetup()
                .body(buildJsonWithPaResponse())
                .post(authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(INTERNAL_SERVER_ERROR.getStatusCode())
                .contentType(JSON)
                .body("message", contains(expectedErrorMessage))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_ERROR.getValue());
    }

    @Test
    public void shouldReturnStatus500_AWorldpayPaResParseError() {
        String chargeId = createNewCharge(AUTHORISATION_3DS_REQUIRED);

        String expectedErrorMessage = "Failed";
        worldpayMockClient.mockAuthorisationPaResParseError();

        givenSetup()
                .body(buildJsonWithPaResponse())
                .post(authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(INTERNAL_SERVER_ERROR.getStatusCode())
                .contentType(JSON)
                .body("message", contains(expectedErrorMessage))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_ERROR.getValue());
    }
}
