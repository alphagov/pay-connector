package uk.gov.pay.connector.it.resources.worldpay;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import static com.jayway.restassured.http.ContentType.JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_SUCCESS;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildCorporateJsonAuthorisationDetailsFor;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonAuthorisationDetailsFor;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonAuthorisationDetailsWithoutAddress;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class WorldpayCardResourceITest extends ChargingITestBase {

    private String validAuthorisationDetails = buildJsonAuthorisationDetailsFor("4444333322221111", "visa");

    public WorldpayCardResourceITest() {
        super("worldpay");
    }

    @Test
    public void shouldAuthoriseChargeWithoutCorporateCard_ForValidAuthorisationDetails() {

        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        worldpayMockClient.mockAuthorisationSuccess();

        givenSetup()
                .body(validAuthorisationDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .body("status", Matchers.is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.toString());
    }

    @Test
    public void shouldAuthoriseChargeWithCorporateCard_ForValidAuthorisationDetails() {

        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        worldpayMockClient.mockAuthorisationSuccess();

        String corporateCreditAuthDetails = buildCorporateJsonAuthorisationDetailsFor(PayersCardType.CREDIT);

        givenSetup()
                .body(corporateCreditAuthDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .body("status", Matchers.is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.toString());
    }

    @Test
    public void shouldAuthoriseChargeWithoutBillingAddress() throws JsonProcessingException {

        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        worldpayMockClient.mockAuthorisationSuccess();

        String authDetails = buildJsonAuthorisationDetailsWithoutAddress();

        givenSetup()
                .body(authDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .body("status", Matchers.is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.toString());
    }

    @Test
    public void shouldReturnStatusAsRequires3ds() {
        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        worldpayMockClient.mockAuthorisationRequires3ds();

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

        worldpayMockClient.mockAuthorisationFailure();

        String expectedErrorMessage = "This transaction was declined.";
        String expectedChargeStatus = AUTHORISATION_REJECTED.getValue();
        shouldReturnErrorForAuthorisationDetailsWithMessage(cardDetailsRejectedByWorldpay, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    public void shouldDeferCaptureCardPayment_IfAsynchronousFeatureFlagIsOn() {
        String chargeId = authoriseNewCharge();

        worldpayMockClient.mockCaptureSuccess();

        givenSetup()
                .post(captureChargeUrlFor(chargeId))
                .then()
                .statusCode(204);

        assertFrontendChargeStatusIs(chargeId, CAPTURE_APPROVED.getValue());
        assertApiStateIs(chargeId, EXTERNAL_SUCCESS.getStatus());
    }

    @Test
    public void shouldAuthoriseCharge_For3dsRequiredCharge() {
        String chargeId = createNewCharge(AUTHORISATION_3DS_REQUIRED);
        worldpayMockClient.mockAuthorisationSuccess();

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
        worldpayMockClient.mockAuthorisationFailure();

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
