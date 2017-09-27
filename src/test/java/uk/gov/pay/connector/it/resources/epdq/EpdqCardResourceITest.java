package uk.gov.pay.connector.it.resources.epdq;

import org.hamcrest.Matchers;
import org.junit.Test;
import uk.gov.pay.connector.it.base.ChargingITestBase;

import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;

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

        String expectedErrorMessage = "ePDQ authorisation response (PAYID: 0, STATUS: 0, NCERROR: 50001111, " +
                "NCERRORPLUS: An error has occurred; please try again later. If you are the owner or the integrator " +
                "of this website, please log into the  back office to see the details of the error.)";
        String expectedChargeStatus = AUTHORISATION_ERROR.getValue();
        shouldReturnErrorForAuthorisationDetailsWithMessage(authorisationDetails, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    public void shouldNotAuthorise_aTransactionInAnyOtherNonSupportedState() throws Exception {
        epdq.mockAuthorisationOther();

        String expectedErrorMessage = "ePDQ authorisation response (PAYID: 3014644340, STATUS: 52, NCERROR: 0, NCERRORPLUS: !)";
        String expectedChargeStatus = AUTHORISATION_ERROR.getValue();
        shouldReturnErrorForAuthorisationDetailsWithMessage(authorisationDetails, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    public void shouldNotAuthorise_aTransactionInWaitingExternalState() throws Exception {
        epdq.mockAuthorisationWaitingExternal();

        String expectedErrorMessage = "This transaction was deferred.";
        String expectedChargeStatus = AUTHORISATION_SUBMITTED.getValue();
        shouldReturnErrorForAuthorisationDetailsWithMessage(authorisationDetails, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    public void shouldNotAuthorise_aTransactionInWaitingState() throws Exception {
        epdq.mockAuthorisationWaiting();

        String expectedErrorMessage = "This transaction was deferred.";
        String expectedChargeStatus = AUTHORISATION_SUBMITTED.getValue();
        shouldReturnErrorForAuthorisationDetailsWithMessage(authorisationDetails, expectedErrorMessage, expectedChargeStatus);
    }
}
