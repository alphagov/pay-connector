package uk.gov.pay.connector.it.resources;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import uk.gov.pay.connector.it.base.CardResourceITestBase;

import java.util.Map;

import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_SUCCESS;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_ERROR_GATEWAY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class CardCaptureResourceITest extends CardResourceITestBase {

    public CardCaptureResourceITest() {
        super("sandbox");
    }

    @Test
    public void shouldFailPayment_IfCaptureStatusIsUnknown() {
        String failedChargeId = createNewChargeWith(CAPTURE_ERROR, randomUUID().toString());
        assertApiStateIs(failedChargeId, EXTERNAL_ERROR_GATEWAY.getStatus());
    }

    @Test
    public void shouldSubmitForCaptureTheCardPayment_IfChargeWasPreviouslyAuthorised() {
        String chargeId = authoriseNewCharge();
        givenSetup()
                .post(captureChargeUrlFor(chargeId))
                .then()
                .statusCode(204);

        assertFrontendChargeStatusIs(chargeId, CAPTURED.getValue());
        assertApiStateIs(chargeId, EXTERNAL_SUCCESS.getStatus());
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

    @Test
    public void shouldRemoveConfirmationDetails_IfCaptureReady() throws Exception {
        String externalChargeId = authoriseNewCharge();
        Long chargeId = Long.valueOf(StringUtils.removeStart(externalChargeId, "charge-"));

        Map<String, Object> confirmationDetails = app.getDatabaseTestHelper().getConfirmationDetailsByChargeId(chargeId);
        assertThat(confirmationDetails.isEmpty(), is(false));

        givenSetup()
                .post(captureChargeUrlFor(externalChargeId))
                .then()
                .statusCode(204);

        confirmationDetails = app.getDatabaseTestHelper().getConfirmationDetailsByChargeId(chargeId);
        assertThat(confirmationDetails, is(nullValue()));
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
