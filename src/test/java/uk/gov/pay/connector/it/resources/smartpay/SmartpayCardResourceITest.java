package uk.gov.pay.connector.it.resources.smartpay;

import com.amazonaws.util.json.Jackson;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_SUCCESS;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonApplePayAuthorisationDetails;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonAuthorisationDetailsFor;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class SmartpayCardResourceITest extends ChargingITestBase {

    private String validCardDetails = buildCardDetailsWith("737");
    private String validApplePayAuthorisationDetails = buildJsonApplePayAuthorisationDetails("mr payment", "mr@payment.test");

    public SmartpayCardResourceITest() {
        super("smartpay");
    }

    @Test
    public void shouldAuthoriseCharge_ForValidCardDetails() {

        String chargeId = createNewChargeWithNoTransactionId(ChargeStatus.ENTERING_CARD_DETAILS);

        smartpayMockClient.mockAuthorisationSuccess();

        givenSetup()
                .body(validCardDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());
    }

    @Test
    public void shouldNotAuthorise_ASmartpayErrorCard() {
        String cardWithWrongCVC = buildCardDetailsWith("999");
        smartpayMockClient.mockAuthorisationFailure();

        String expectedErrorMessage = "This transaction was declined.";
        String expectedChargeStatus = AUTHORISATION_REJECTED.getValue();
        shouldReturnErrorForAuthorisationDetailsWithMessage(cardWithWrongCVC, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    public void shouldAuthorise_whenRequires3dsAnd3dsAuthenticationSuccessful() {
        databaseTestHelper.enable3dsForGatewayAccount(Long.parseLong(accountId));
        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        smartpayMockClient.mockAuthorisation3dsRequired();

        ValidatableResponse response = givenSetup()
                .body(validCardDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then();

        response
                .body("status", is(AUTHORISATION_3DS_REQUIRED.toString()))
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_3DS_REQUIRED.toString());
    }

    @Test
    public void shouldSuccessWhenAuth3dsRequiredAndAuthorisationSuccess() throws JsonProcessingException {
        databaseTestHelper.enable3dsForGatewayAccount(Long.parseLong(accountId));
        String chargeId = createNewChargeWithNoTransactionId(AUTHORISATION_3DS_REQUIRED);
        smartpayMockClient.mockAuthorisationSuccess();

        givenSetup()
                .body(new ObjectMapper().writeValueAsString(get3dsPayload()))
                .post(authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());
    }

    @Test
    public void shouldFailWhenAuth3dsRequiredAndAuthorisationFailure() throws JsonProcessingException {
        databaseTestHelper.enable3dsForGatewayAccount(Long.parseLong(accountId));
        String chargeId = createNewChargeWithNoTransactionId(AUTHORISATION_3DS_REQUIRED);
        smartpayMockClient.mockAuthorisationFailure();

        givenSetup()
                .body(new ObjectMapper().writeValueAsString(get3dsPayload()))
                .post(authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(400);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_REJECTED.getValue());
    }

    @Test
    public void shouldReturnStatus500_WhenAuthorisationCallThrowsException() throws JsonProcessingException {
        databaseTestHelper.enable3dsForGatewayAccount(Long.parseLong(accountId));
        String chargeId = createNewChargeWithNoTransactionId(AUTHORISATION_3DS_REQUIRED);
        smartpayMockClient.mockServerFault();

        givenSetup()
                .body(new ObjectMapper().writeValueAsString(get3dsPayload()))
                .post(authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(500);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_ERROR.getValue());
    }

    @Test
    public void shouldCaptureCardPayment_IfChargeWasPreviouslyAuthorised() {
        String chargeId = authoriseNewCharge();

        smartpayMockClient.mockCaptureSuccess();

        givenSetup()
                .post(captureChargeUrlFor(chargeId))
                .then()
                .statusCode(204);

        assertFrontendChargeStatusIs(chargeId, CAPTURE_APPROVED.getValue());
        assertApiStateIs(chargeId, EXTERNAL_SUCCESS.getStatus());
    }

    @Test
    public void shouldReturnBadRequestResponseWhenTryingToAuthoriseAnApplePayPayment() {
        String chargeId = createNewChargeWithNoTransactionId(ChargeStatus.ENTERING_CARD_DETAILS);

        given().port(testContext.getPort())
                .contentType(JSON)
                .body(validApplePayAuthorisationDetails)
                .post(authoriseChargeUrlForApplePay(chargeId))
                .then()
                .statusCode(400)
                .body("message", contains("Wallets are not supported for Smartpay"));
    }

    @Test
    public void shouldReturnBadRequestResponseWhenTryingToAuthoriseAGooglePayPayment() throws IOException {
        String chargeId = createNewChargeWithNoTransactionId(ChargeStatus.ENTERING_CARD_DETAILS);
        JsonNode validPayload = Jackson.getObjectMapper().readTree(
                fixture("googlepay/example-auth-request.json"));
        
        given().port(testContext.getPort())
                .contentType(JSON)
                .body(validPayload)
                .post(authoriseChargeUrlForGooglePay(chargeId))
                .then()
                .statusCode(400)
                .body("message", contains("Wallets are not supported for Smartpay"));
    }
    
    @Test
    public void shouldPersistTransactionIds_duringAuthorisationAndCapture() {
        String externalChargeId = createNewChargeWithNoTransactionId(ChargeStatus.ENTERING_CARD_DETAILS);
        String pspReference1 = "pspRef1-" + UUID.randomUUID().toString();
        smartpayMockClient.mockAuthorisationWithTransactionId(pspReference1);

        givenSetup()
                .body(validCardDetails)
                .post(authoriseChargeUrlFor(externalChargeId))
                .then()
                .statusCode(200);

        assertFrontendChargeStatusIs(externalChargeId, AUTHORISATION_SUCCESS.getValue());

        String pspReference2 = "pspRef2-" + UUID.randomUUID().toString();
        smartpayMockClient.mockCaptureSuccessWithTransactionId(pspReference2);

        givenSetup()
                .post(captureChargeUrlFor(externalChargeId))
                .then()
                .statusCode(204);
        assertApiStateIs(externalChargeId, EXTERNAL_SUCCESS.getStatus());
        long chargeId = Long.parseLong(StringUtils.removeStart(externalChargeId, "charge-"));
        List<Map<String, Object>> chargeEvents = databaseTestHelper.getChargeEvents(chargeId);

        assertThat(chargeEvents, hasEvent(AUTHORISATION_SUCCESS));
        assertThat(chargeEvents, hasEvent(CAPTURE_APPROVED));
    }

    @Test
    public void shouldCancelCharge() {
        String chargeId = createNewCharge(AUTHORISATION_SUCCESS);

        smartpayMockClient.mockCancel();

        givenSetup()
                .contentType(ContentType.JSON)
                .post(cancelChargeUrlFor(accountId, chargeId))
                .then()
                .statusCode(204);
    }

    private String buildCardDetailsWith(String cvc) {

        return buildJsonAuthorisationDetailsFor(
                "Mr.Payment",
                "5555444433331111",
                cvc,
                "08/18",
                "visa",
                "The Money Pool", "line 2",
                "London",
                null, "DO11 4RS", "GB");
    }

    private static ImmutableMap<String, String> get3dsPayload() {
        return ImmutableMap.of(
                "pa_response", "some pa response",
                "md", "some md text"
        );
    }
}
