package uk.gov.pay.connector.it.resources.smartpay;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.ValidatableResponse;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;

public class SmartpayCardResourceITest extends ChargingITestBase {

    private String validCardDetails = buildCardDetailsWith("737");

    public SmartpayCardResourceITest() {
        super("smartpay");
    }

    @Test
    public void shouldAuthoriseCharge_ForValidCardDetails() throws Exception {

        String chargeId = createNewChargeWithNoTransactionId(ChargeStatus.ENTERING_CARD_DETAILS);

        smartpay.mockAuthorisationSuccess();

        givenSetup()
                .body(validCardDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());
    }

    @Test
    public void shouldNotAuthorise_ASmartpayErrorCard() throws Exception {
        String cardWithWrongCVC = buildCardDetailsWith("999");
        smartpay.mockAuthorisationFailure();

        String expectedErrorMessage = "This transaction was declined.";
        String expectedChargeStatus = AUTHORISATION_REJECTED.getValue();
        shouldReturnErrorForAuthorisationDetailsWithMessage(cardWithWrongCVC, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    public void shouldAuthorise_whenRequires3dsAnd3dsAuthenticationSuccessful() {
        app.getDatabaseTestHelper().enable3dsForGatewayAccount(Long.parseLong(accountId));
        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        smartpay.mockAuthorisation3dsRequired();

        ValidatableResponse response = givenSetup()
                .body(validCardDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then();

        response
                .body("status", is(AUTHORISATION_3DS_REQUIRED.toString()))
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_3DS_REQUIRED.toString());
    }

    @Test
    public void shouldSuccessWhenAuth3dsRequiredAndAuthorisationSuccess() throws JsonProcessingException {
        app.getDatabaseTestHelper().enable3dsForGatewayAccount(Long.parseLong(accountId));
        String chargeId = createNewChargeWithNoTransactionId(AUTHORISATION_3DS_REQUIRED);
        smartpay.mockAuthorisationSuccess();

        givenSetup()
                .body(new ObjectMapper().writeValueAsString(get3dsPayload()))
                .post(authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(200);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());
    }

    @Test
    public void shouldFailWhenAuth3dsRequiredAndAuthorisationFailure() throws JsonProcessingException {
        app.getDatabaseTestHelper().enable3dsForGatewayAccount(Long.parseLong(accountId));
        String chargeId = createNewChargeWithNoTransactionId(AUTHORISATION_3DS_REQUIRED);
        smartpay.mockAuthorisationFailure();

        givenSetup()
                .body(new ObjectMapper().writeValueAsString(get3dsPayload()))
                .post(authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(400);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_REJECTED.getValue());
    }

    @Test
    public void shouldCaptureCardPayment_IfChargeWasPreviouslyAuthorised() {
        String chargeId = authoriseNewCharge();

        smartpay.mockCaptureSuccess();

        givenSetup()
                .post(captureChargeUrlFor(chargeId))
                .then()
                .statusCode(204);

        assertFrontendChargeStatusIs(chargeId, CAPTURE_APPROVED.getValue());
        assertApiStateIs(chargeId, EXTERNAL_SUCCESS.getStatus());
    }

    @Test
    public void shouldPersistTransactionIds_duringAuthorisationAndCapture() throws Exception {
        String externalChargeId = createNewChargeWithNoTransactionId(ChargeStatus.ENTERING_CARD_DETAILS);
        String pspReference1 = "pspRef1-" + UUID.randomUUID().toString();
        smartpay.mockAuthorisationWithTransactionId(pspReference1);

        givenSetup()
                .body(validCardDetails)
                .post(authoriseChargeUrlFor(externalChargeId))
                .then()
                .statusCode(200);

        assertFrontendChargeStatusIs(externalChargeId, AUTHORISATION_SUCCESS.getValue());

        String pspReference2 = "pspRef2-" + UUID.randomUUID().toString();
        smartpay.mockCaptureSuccessWithTransactionId(pspReference2);

        givenSetup()
                .post(captureChargeUrlFor(externalChargeId))
                .then()
                .statusCode(204);
        assertApiStateIs(externalChargeId, EXTERNAL_SUCCESS.getStatus());
        long chargeId = Long.parseLong(StringUtils.removeStart(externalChargeId, "charge-"));
        List<Map<String, Object>> chargeEvents = app.getDatabaseTestHelper().getChargeEvents(chargeId);

        assertThat(chargeEvents, hasEvent(AUTHORISATION_SUCCESS));
        assertThat(chargeEvents, hasEvent(CAPTURE_APPROVED));
    }

    @Test
    public void shouldCancelCharge() {
        String chargeId = createNewCharge(AUTHORISATION_SUCCESS);

        smartpay.mockCancel();

        givenSetup()
                .contentType(ContentType.JSON)
                .post(cancelChargeUrlFor(accountId, chargeId))
                .then()
                .statusCode(204);
    }

    private String buildCardDetailsWith(String cvc) {

        return buildJsonAuthorisationDetailsFor(
                "Mr.Payment",
                "5555444433331111",
                cvc,
                "08/18",
                "visa",
                "The Money Pool", "line 2",
                "London",
                null, "DO11 4RS", "GB");
    }

    private static ImmutableMap<String, String> get3dsPayload() {
        return ImmutableMap.of(
                "pa_response", "some pa response",
                "md", "some md text"
        );
    }
}
