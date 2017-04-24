package uk.gov.pay.connector.it.resources.worldpay;

import org.hamcrest.Matchers;
import org.junit.Test;
import uk.gov.pay.connector.it.base.ChargingITestBase;

import static com.jayway.restassured.http.ContentType.JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class WorldpayCardResourceITest extends ChargingITestBase {

    private String validAuthorisationDetails = buildJsonAuthorisationDetailsFor("4444333322221111", "visa");

    public WorldpayCardResourceITest() {
        super("worldpay");
    }

    @Test
    public void shouldAuthoriseCharge_ForValidAuthorisationDetails() throws Exception {

        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        worldpay.mockAuthorisationSuccess();

        givenSetup()
                .body(validAuthorisationDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .body("status", Matchers.is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.toString());
    }

    @Test
    public void shouldReturnStatusAsRequires3ds() {
        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        worldpay.mockAuthorisationRequires3ds();

        givenSetup()
                .body(validAuthorisationDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .body("status", Matchers.is(AUTHORISATION_3DS_REQUIRED.toString()))
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_3DS_REQUIRED.toString());
    }

    @Test
    public void shouldNotAuthorise_AWorldpayErrorCard() throws Exception {
        String cardDetailsRejectedByWorldpay = buildJsonAuthorisationDetailsFor("REFUSED", "4444333322221111", "visa");

        worldpay.mockAuthorisationFailure();

        String expectedErrorMessage = "This transaction was declined.";
        String expectedChargeStatus = AUTHORISATION_REJECTED.getValue();
        shouldReturnErrorForAuthorisationDetailsWithMessage(cardDetailsRejectedByWorldpay, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    public void shouldCaptureCardPayment_IfChargeWasPreviouslyAuthorised() {
        String chargeId = authoriseNewCharge();

        worldpay.mockCaptureSuccess();

        givenSetup()
                .post(captureChargeUrlFor(chargeId))
                .then()
                .statusCode(204);

        assertFrontendChargeStatusIs(chargeId, CAPTURE_SUBMITTED.getValue());
        assertApiStateIs(chargeId, EXTERNAL_SUCCESS.getStatus());
    }

    @Test
    public void shouldAuthoriseCharge_For3dsRequiredCharge() {
        String chargeId = createNewCharge(AUTHORISATION_3DS_REQUIRED);
        worldpay.mockAuthorisationSuccess();

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
        worldpay.mockAuthorisationFailure();

        givenSetup()
                .body(buildJsonWithPaResponse())
                .post(authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", is(expectedErrorMessage));

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_REJECTED.getValue());
    }

}
