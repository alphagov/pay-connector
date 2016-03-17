package uk.gov.pay.connector.it.resources;

import org.junit.Test;
import uk.gov.pay.connector.it.base.CardResourceITestBase;

import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.EXT_FAILED;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.EXT_SUCCEEDED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class CardCaptureResourceITest extends CardResourceITestBase {

    public CardCaptureResourceITest() {
        super("sandbox");
    }

    @Test
    public void shouldFailPayment_IfCaptureStatusIsUnknown() {
        String failedChargeId = createNewChargeWith(CAPTURE_ERROR, randomUUID().toString());
        assertApiStatusIs(failedChargeId, EXT_FAILED.getValue());
    }

    @Test
    public void shouldSubmitForCaptureTheCardPayment_IfChargeWasPreviouslyAuthorised() {
        String chargeId = authoriseNewCharge();
        givenSetup()
                .post(captureChargeUrlFor(chargeId))
                .then()
                .statusCode(204);

        assertFrontendChargeStatusIs(chargeId, CAPTURE_SUBMITTED.getValue());
        assertApiStatusIs(chargeId, EXT_SUCCEEDED.getValue());
    }

    @Test
    public void shouldReturn404IfChargeDoesNotExist_ForCapture() {
        String unknownId = "398579438759438";

        givenSetup()
                .post(captureChargeUrlFor(unknownId))
                .then()
                .statusCode(404)
                .contentType(JSON)
                .body("message", is(format("Charge with id [%s] not found.", unknownId)));

    }

    @Test
    public void shouldReturnErrorWithoutChangingChargeState_IfOriginalStateIsNotAuthorised() {
        String chargeId = createNewChargeWith(ENTERING_CARD_DETAILS, null);

        givenSetup()
                .post(captureChargeUrlFor(chargeId))
                .then()
                .statusCode(500)
                .contentType(JSON)
                .body("message", is("Charge not in correct state to be processed, " + chargeId));


        assertFrontendChargeStatusIs(chargeId, ENTERING_CARD_DETAILS.getValue());
    }

    @Test
    public void shouldReturnErrorAndDoNotUpdateChargeStatus_IfCaptureAlreadyInProgress() throws Exception {
        String chargeId = createNewChargeWith(CAPTURE_READY, null);

        givenSetup()
                .post(captureChargeUrlFor(chargeId))
                .then()
                .statusCode(202)
                .contentType(JSON)
                .body("message", is(format("Capture for charge already in progress, %s", chargeId)));

        assertFrontendChargeStatusIs(chargeId, CAPTURE_READY.getValue());
    }
}
