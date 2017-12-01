package uk.gov.pay.connector.it.resources.smartpay;

import com.jayway.restassured.http.ContentType;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class SmartpayCardResourceDropwizardITest extends ChargingITestBase {

    private String validCardDetails = buildCardDetailsWith("737");

    public SmartpayCardResourceDropwizardITest() {
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
}
