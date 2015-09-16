package uk.gov.pay.connector.it;

import org.junit.Test;

import static com.jayway.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.model.ChargeStatus.AUTHORISATION_SUBMITTED;

public class ChargeCaptureResourceITest extends ChargeCaptureResourceITestBase {

    public ChargeCaptureResourceITest() {
        super("sandbox");
    }

    @Test
    public void shouldConfirmCardPaymentIfChargeWasAuthorised() {
        String chargeId = authoriseNewCharge();

        givenSetup()
                .post(chargeCaptureUrlFor(chargeId))
                .then()
                .statusCode(204);

        assertFrontendChargeStatusIs(chargeId, "CAPTURED");
        assertApiStatusIs(chargeId, "SUCCEEDED");
    }

    @Test
    public void shouldReturnErrorWithoutChangingChargeState_IfOriginalStateIsNotAuthSuccess() {
        String chargeIdNotAuthorised = createNewChargeWithStatus(AUTHORISATION_SUBMITTED);

        givenSetup()
                .post(chargeCaptureUrlFor(chargeIdNotAuthorised))
                .then()
                .statusCode(400)
                .contentType(JSON)
                .body("message", is("Cannot capture a charge with status " + AUTHORISATION_SUBMITTED.getValue() + "."));

        assertFrontendChargeStatusIs(chargeIdNotAuthorised, "AUTHORISATION SUBMITTED");
    }

    @Test
    public void shouldReturn404iftheChargeCannotBeFound() {
        String unknownChargeId = "398579438759438";

        givenSetup()
                .post(chargeCaptureUrlFor(unknownChargeId))
                .then()
                .statusCode(404);
    }

}
