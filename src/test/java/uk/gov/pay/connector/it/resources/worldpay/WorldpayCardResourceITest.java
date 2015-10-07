package uk.gov.pay.connector.it.resources.worldpay;

import org.junit.Test;
import uk.gov.pay.connector.it.base.CardResourceITestBase;

import static uk.gov.pay.connector.model.api.ExternalChargeStatus.EXT_IN_PROGRESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class WorldpayCardResourceITest extends CardResourceITestBase {

    private String validCardDetails = buildJsonCardDetailsFor("4444333322221111");

    public WorldpayCardResourceITest() {
        super("worldpay");
    }

    @Test
    public void shouldAuthoriseCharge_ForValidCardDetails() throws Exception {

        String chargeId = createNewCharge();
        worldpay.mockAuthorisationSuccess();

        givenSetup()
                .body(validCardDetails)
                .post(cardUrlFor(chargeId))
                .then()
                .statusCode(204);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());
    }

    @Test
    public void shouldNotAuthorise_AWorldpayErrorCard() throws Exception {
        String cardDetailsRejectedByWorldpay = buildJsonCardDetailsFor("REFUSED", "4444333322221111");

        worldpay.mockAuthorisationFailure();

        String expectedErrorMessage = "This transaction was declined.";
        String expectedChargeStatus = AUTHORISATION_REJECTED.getValue();
        shouldReturnErrorForCardDetailsWithMessage(cardDetailsRejectedByWorldpay, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    public void shouldCaptureCardPayment_IfChargeWasPreviouslyAuthorised() {
        String chargeId = authoriseNewCharge();

        worldpay.mockCaptureResponse();

        givenSetup()
                .post(chargeCaptureUrlFor(chargeId))
                .then()
                .statusCode(204);

        assertFrontendChargeStatusIs(chargeId, CAPTURE_SUBMITTED.getValue());
        assertApiStatusIs(chargeId, EXT_IN_PROGRESS.getValue());
    }
}
