package uk.gov.pay.connector.it.resources.epdq;

import org.hamcrest.Matchers;
import org.junit.Test;
import uk.gov.pay.connector.it.base.ChargingITestBase;

import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class EpdqCardResourceITest extends ChargingITestBase {

    private String authorisationDetails = buildJsonAuthorisationDetailsFor("4444333322221111", "visa");

    public EpdqCardResourceITest() {
        super("epdq");
    }

    @Test
    public void shouldAuthorise_whenTransactionIsSuccessful() throws Exception {

        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        epdq.mockAuthorisationSuccess();

        givenSetup()
                .body(authorisationDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .body("status", Matchers.is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.toString());
    }

    @Test
    public void shouldNotAuthorise_whenTransactionIsRefused() throws Exception {
        epdq.mockAuthorisationFailure();

        String expectedErrorMessage = "This transaction was declined.";
        String expectedChargeStatus = AUTHORISATION_REJECTED.getValue();
        shouldReturnErrorForAuthorisationDetailsWithMessage(authorisationDetails, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    public void shouldNotAuthorise_whenTransactionIsInError() throws Exception {
        epdq.mockAuthorisationError();

        String expectedErrorMessage = "[50001111] An error has occurred; please try again later. If you are the owner or the integrator of this website, please log into the  back office to see the details of the error.";
        String expectedChargeStatus = AUTHORISATION_ERROR.getValue();
        shouldReturnErrorForAuthorisationDetailsWithMessage(authorisationDetails, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    public void shouldNotAuthorise_aTransactionInAnyOtherNonSupportedState() throws Exception {
        epdq.mockAuthorisationOther();

        String expectedErrorMessage = "[0] !";
        String expectedChargeStatus = AUTHORISATION_ERROR.getValue();
        shouldReturnErrorForAuthorisationDetailsWithMessage(authorisationDetails, expectedErrorMessage, expectedChargeStatus);
    }
}
