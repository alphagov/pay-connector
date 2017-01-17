package uk.gov.pay.connector.it.resources.smartpay;

import com.jayway.restassured.http.ContentType;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.util.TransactionId.randomId;

public class SmartpayCardResourceITest extends ChargingITestBase {

    private String validCardDetails = buildCardDetailsWith("737");

    public SmartpayCardResourceITest() {
        super("smartpay");
    }

    @Test
    public void shouldAuthoriseCharge_ForValidCardDetails() throws Exception {

        String chargeId = createNewChargeWith(ChargeStatus.ENTERING_CARD_DETAILS, null);

        smartpay.mockAuthorisationSuccess();

        givenSetup()
                .body(validCardDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .statusCode(204);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());
    }

    @Test
    public void shouldNotAuthorise_ASmartpayErrorCard() throws Exception {
        String cardWithWrongCVC = buildCardDetailsWith("999");

        smartpay.mockAuthorisationFailure();

        String expectedErrorMessage = "This transaction was declined.";
        String expectedChargeStatus = AUTHORISATION_REJECTED.getValue();
        shouldReturnErrorForCardDetailsWithMessage(cardWithWrongCVC, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    public void shouldCaptureCardPayment_IfChargeWasPreviouslyAuthorised() {
        String chargeId = authoriseNewCharge();

        smartpay.mockCaptureResponse();

        givenSetup()
                .post(captureChargeUrlFor(chargeId))
                .then()
                .statusCode(204);

        assertFrontendChargeStatusIs(chargeId, CAPTURE_SUBMITTED.getValue());
        assertApiStateIs(chargeId, EXTERNAL_SUCCESS.getStatus());
    }

    @Test
    public void shouldPersistTransactionIds_duringAuthorisationAndCapture() throws Exception {
        String externalChargeId = createNewChargeWith(ChargeStatus.ENTERING_CARD_DETAILS, null);
        String pspReference1 = "pspRef1-" + UUID.randomUUID().toString();
        smartpay.mockAuthorisationWithTransactionId(pspReference1);

        givenSetup()
                .body(validCardDetails)
                .post(authoriseChargeUrlFor(externalChargeId))
                .then()
                .statusCode(204);

        assertFrontendChargeStatusIs(externalChargeId, AUTHORISATION_SUCCESS.getValue());

        String pspReference2 = "pspRef2-" + UUID.randomUUID().toString();
        smartpay.mockCaptureResponseWithTransactionId(pspReference2);

        givenSetup()
                .post(captureChargeUrlFor(externalChargeId))
                .then()
                .statusCode(204);
        assertApiStateIs(externalChargeId, EXTERNAL_SUCCESS.getStatus());
        long chargeId = Long.parseLong(StringUtils.removeStart(externalChargeId, "charge-"));
        List<Map<String, Object>> chargeEvents = app.getDatabaseTestHelper().getChargeEvents(chargeId);

        assertThat(chargeEvents, hasEvent(AUTHORISATION_SUCCESS));
        assertThat(chargeEvents, hasEvent(CAPTURE_SUBMITTED));
    }

    @Test
    public void shouldCancelCharge() {
        String gatewayTransactionId = randomId();
        String chargeId = createNewChargeWith(AUTHORISATION_SUCCESS, gatewayTransactionId);

        smartpay.mockCancelResponse();

        givenSetup()
                .contentType(ContentType.JSON)
                .post(cancelChargeUrlFor(accountId, chargeId))
                .then()
                .statusCode(204);
    }

    @Test
    public void shouldBadRequest_ASmartpayError() {
        String chargeId = createNewChargeWith(AUTHORISATION_SUCCESS, randomId());

        smartpay.mockErrorResponse();

        givenSetup()
                .post(captureChargeUrlFor(chargeId))
                .then()
                .statusCode(400)
                .body("message", is("[soap:Server] validation 167 Original pspReference required for this operation"));
    }

    private String buildCardDetailsWith(String cvc) {

        return buildJsonCardDetailsFor(
                "Mr.Payment",
                "5555444433331111",
                cvc,
                "08/18",
                "visa",
                "The Money Pool", "line 2",
                "London",
                null, "DO11 4RS", "GB");
    }
}
