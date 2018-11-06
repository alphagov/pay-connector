package uk.gov.pay.connector.it.resources.worldpay;

import com.google.gson.JsonObject;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import static com.jayway.restassured.http.ContentType.JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_SUCCESS;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class WorldpayCardResourceITest extends ChargingITestBase {

    private String validApplePayAuthorisationDetails = buildApplePayAuthorisationDetails();

    public WorldpayCardResourceITest() {
        super("worldpay");
    }

    @Test
    public void shouldAuthoriseChargeWithoutCorporateCard_ForValidAuthorisationDetails() {

        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        worldpayMockClient.mockAuthorisationSuccess();

        givenSetup()
                .body(validApplePayAuthorisationDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .body("status", Matchers.is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.toString());
    }
    
    @Test
    public void shouldAuthoriseChargeWithApplePay_ForValidAuthorisationDetails() {

        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        worldpayMockClient.mockAuthorisationSuccess();

        givenSetup()
                .body(validApplePayAuthorisationDetails)
                .post("/v1/frontend/charges/{chargeId}/wallets".replace("{chargeId}", chargeId))
                .then()
                .body("status", Matchers.is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.toString());
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
                .body("status", Matchers.is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.toString());
    }

    @Test
    public void shouldReturnStatusAsRequires3ds() {
        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        worldpayMockClient.mockAuthorisationRequires3ds();

        givenSetup()
                .body(validApplePayAuthorisationDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .body("status", Matchers.is(AUTHORISATION_3DS_REQUIRED.toString()))
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_3DS_REQUIRED.toString());
    }

    @Test
    public void shouldNotAuthorise_AWorldpayErrorCard() throws Exception {
        String cardDetailsRejectedByWorldpay = buildJsonAuthorisationDetailsFor("REFUSED", "4444333322221111", "visa");

        worldpayMockClient.mockAuthorisationFailure();

        String expectedErrorMessage = "This transaction was declined.";
        String expectedChargeStatus = AUTHORISATION_REJECTED.getValue();
        shouldReturnErrorForAuthorisationDetailsWithMessage(cardDetailsRejectedByWorldpay, expectedErrorMessage, expectedChargeStatus);
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
                .body("message", is(expectedErrorMessage));

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_REJECTED.getValue());
    }

    private static String buildApplePayAuthorisationDetails() {
        JsonObject header = new JsonObject();
        header.addProperty("public_key_hash", "some");
        header.addProperty("ephemeral_public_key", "some");
        header.addProperty("transaction_id", "some");
        header.addProperty("application_data", "some");
        header.addProperty("wrapped_key", "some");

        JsonObject detailedPaymentData = new JsonObject();
        detailedPaymentData.addProperty("onlinePaymentCryptogram", "some");
        detailedPaymentData.addProperty("eciIndicator", "some");

        JsonObject paymentData = new JsonObject();
        paymentData.addProperty("applicationPrimaryAccountNumber", "some");
        paymentData.addProperty("applicationExpirationDate", "some");
        paymentData.addProperty("currencyCode", "some");
        paymentData.addProperty("transactionAmount", "some");
        paymentData.addProperty("cardholderName", "some");
        paymentData.addProperty("deviceManufacturerIdentifier", "some");
        paymentData.addProperty("paymentDataType", "some");
        paymentData.add("paymentData", detailedPaymentData);

        JsonObject encryptedPaymentData = new JsonObject();
        encryptedPaymentData.addProperty("version", "some");
        encryptedPaymentData.addProperty("signature", "some");
        encryptedPaymentData.add("header", header);
        encryptedPaymentData.add("data", paymentData);

        JsonObject paymentInfo = new JsonObject();
        paymentInfo.addProperty("last_digits_card_number", "1234");
        paymentInfo.addProperty("brand", "visa");
        paymentInfo.addProperty("payers_card_type", "DEBIT");
        paymentInfo.addProperty("cardholder_name", "mr. payment");
        paymentInfo.addProperty("email", "mr@payment.test");

        JsonObject payload = new JsonObject();
        payload.add("payment_info", paymentInfo);
        payload.add("encrypted_payment_data", encryptedPaymentData);
        return toJson(payload);
    }

}
