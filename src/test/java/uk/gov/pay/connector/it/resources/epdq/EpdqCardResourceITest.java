package uk.gov.pay.connector.it.resources.epdq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.response.ValidatableResponse;
import org.junit.Test;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.util.Map;

import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gateway.model.Auth3dsDetails.Auth3dsResult.AUTHORISED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;

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
                .body("status", is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.toString());
    }

    @Test
    public void shouldAuthorise_whenRequires3dsAnd3dsAuthenticationSuccessful() {
        app.getDatabaseTestHelper().enable3dsForGatewayAccount(Long.parseLong(accountId));
        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        epdq.mockAuthorisation3dsSuccess();

        ValidatableResponse response = givenSetup()
                .body(authorisationDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then();

        response
                .body("status", is(AUTHORISATION_3DS_REQUIRED.toString()))
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_3DS_REQUIRED.toString());
    }

    @Test
    public void shouldSuccessfully_authorise3ds() throws Exception {
        app.getDatabaseTestHelper().enable3dsForGatewayAccount(Long.parseLong(accountId));
        String chargeId = createNewChargeWith(AUTHORISATION_3DS_REQUIRED, RandomIdGenerator.newId());
        epdq.mockAuthorisationQuerySuccess();

        Map<String, String> payload = ImmutableMap.of("auth_3ds_result", AUTHORISED.name());

        givenSetup()
                .body(new ObjectMapper().writeValueAsString(payload))
                .post(authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());
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
