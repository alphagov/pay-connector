package uk.gov.pay.connector.it.resources.smartpay;

import com.jayway.restassured.http.ContentType;
import org.junit.Test;
import uk.gov.pay.connector.it.base.CardResourceITestBase;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.util.TransactionId.randomId;

public class SmartpayCardResourceITest extends CardResourceITestBase {

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
    public void shouldCancelCharge() {
        String gatewayTransactionId = randomId();
        String chargeId = createNewChargeWith(AUTHORISATION_SUCCESS, gatewayTransactionId);

        smartpay.mockCancelResponse();

        givenSetup()
                .contentType(ContentType.JSON)
                .post(cancelChargeUrlFor(accountId ,chargeId))
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
