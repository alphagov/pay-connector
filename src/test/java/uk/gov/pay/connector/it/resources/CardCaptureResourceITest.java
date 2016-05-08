package uk.gov.pay.connector.it.resources;

import org.junit.Test;
import uk.gov.pay.connector.it.base.CardResourceITestBase;

import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.model.api.LegacyChargeStatus.LEGACY_EXT_FAILED;
import static uk.gov.pay.connector.model.api.LegacyChargeStatus.LEGACY_EXT_SUCCEEDED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class CardCaptureResourceITest extends CardResourceITestBase {

    public CardCaptureResourceITest() {
        super("sandbox");
    }

    @Test
    public void shouldFailPayment_IfCaptureStatusIsUnknown() {
        String failedChargeId = createNewChargeWith(CAPTURE_ERROR, randomUUID().toString());
        assertApiStatusIs(failedChargeId, LEGACY_EXT_FAILED.getValue());
    }

    @Test
    public void shouldSubmitForCaptureTheCardPayment_IfChargeWasPreviouslyAuthorised() {
        String chargeId = authoriseNewCharge();
        givenSetup()
                .post(captureChargeUrlFor(chargeId))
                .then()
                .statusCode(204);

        assertFrontendChargeStatusIs(chargeId, CAPTURE_SUBMITTED.getValue());
        assertApiStatusIs(chargeId, LEGACY_EXT_SUCCEEDED.getValue());
    }

    @Test
    public void shouldReturn404IfChargeDoesNotExist_ForCapture() {
        String unknownId = "398579438759438";
        String message = String.format("Charge with id [%s] not found.", unknownId);

        captureAndVerifyFor(unknownId, 404, message);
    }

    @Test
    public void shouldReturnErrorWithoutChangingChargeState_IfOriginalStateIsNotAuthorised() {
        String chargeId = createNewChargeWith(ENTERING_CARD_DETAILS, null);
        String message = "Charge not in correct state to be processed, " + chargeId;
        captureAndVerifyFor(chargeId, 400, message);

        assertFrontendChargeStatusIs(chargeId, ENTERING_CARD_DETAILS.getValue());
    }

    @Test
    public void shouldReturnErrorAndDoNotUpdateChargeStatus_IfCaptureAlreadyInProgress() throws Exception {
        String chargeId = createNewChargeWith(CAPTURE_READY, null);

        String message = format("Capture for charge already in progress, %s", chargeId);
        captureAndVerifyFor(chargeId, 202, message);

        assertFrontendChargeStatusIs(chargeId, CAPTURE_READY.getValue());
    }

    @Test
    public void shouldReturnCaptureError_IfChargeExpired() throws Exception {
        String chargeId = createNewChargeWith(EXPIRED, null);
        String message = format("Capture for charge failed as already expired, %s", chargeId);
        captureAndVerifyFor(chargeId, 400, message);
        assertFrontendChargeStatusIs(chargeId, EXPIRED.getValue());
    }

    private void captureAndVerifyFor(String chargeId, int expectedStatusCode, String message) {
        givenSetup()
                .post(captureChargeUrlFor(chargeId))
                .then()
                .statusCode(expectedStatusCode)
                .contentType(JSON)
                .body("message", is(message));
    }

}
