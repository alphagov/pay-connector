package uk.gov.pay.connector.it.resources;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.common.model.api.ErrorIdentifier;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import java.util.Map;

import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_ERROR_GATEWAY;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_SUCCESS;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class CardResourceCaptureITest extends ChargingITestBase {

    public CardResourceCaptureITest() {
        super("sandbox");
    }

    @Test
    public void shouldFailPayment_IfCaptureStatusIsUnknown() {
        String failedChargeId = createNewCharge(CAPTURE_ERROR);
        assertApiStateIs(failedChargeId, EXTERNAL_ERROR_GATEWAY.getStatus());
    }

    @Test
    public void shouldSubmitForCaptureTheCardPayment_IfChargeWasPreviouslyAuthorised() {
        String chargeId = authoriseNewCharge();
        givenSetup()
                .post(captureChargeUrlFor(chargeId))
                .then()
                .statusCode(204);

        assertFrontendChargeStatusIs(chargeId, CAPTURE_APPROVED.getValue());
        assertApiStateIs(chargeId, EXTERNAL_SUCCESS.getStatus());
    }

    @Test
    public void shouldReturn404IfChargeDoesNotExist_ForCapture() {
        String unknownId = "398579438759438";
        String message = String.format("Charge with id [%s] not found.", unknownId);

        captureAndVerifyFor(unknownId, 404, message);
    }

    @Test
    public void shouldReturnErrorWithoutChangingChargeState_IfChargeIsCaptureReady() {
        String chargeId = createNewChargeWithNoTransactionId(CAPTURE_READY);
        String message = "Charge not in correct state to be processed, " + chargeId;
        captureAndVerifyFor(chargeId, 400, message);

        assertFrontendChargeStatusIs(chargeId, CAPTURE_READY.getValue());
    }

    @Test
    public void shouldReturnErrorWithoutChangingChargeState_IfChargeIsExpired() {
        String chargeId = createNewChargeWithNoTransactionId(EXPIRED);
        String message = format("Charge not in correct state to be processed, %s", chargeId);
        captureAndVerifyFor(chargeId, 400, message);
        assertFrontendChargeStatusIs(chargeId, EXPIRED.getValue());
    }

    @Test
    public void shouldPreserveCardDetails_IfCaptureReady() {
        String externalChargeId = authoriseNewCharge();
        Long chargeId = Long.valueOf(StringUtils.removeStart(externalChargeId, "charge-"));

        Map<String, Object> chargeCardDetails = databaseTestHelper.getChargeCardDetailsByChargeId(chargeId);
        assertThat(chargeCardDetails.isEmpty(), is(false));

        givenSetup()
                .post(captureChargeUrlFor(externalChargeId))
                .then()
                .statusCode(204);

        chargeCardDetails = databaseTestHelper.getChargeCardDetailsByChargeId(chargeId);
        assertThat(chargeCardDetails, is(notNullValue()));
        assertThat(chargeCardDetails.get("last_digits_card_number"), is(notNullValue()));
        assertThat(chargeCardDetails.get("first_digits_card_number"), is(notNullValue()));
        assertThat(chargeCardDetails.get("expiry_date"), is(notNullValue()));
        assertThat(chargeCardDetails.get("card_brand"), is(notNullValue()));
        assertThat(chargeCardDetails.get("cardholder_name"), is(notNullValue()));
        assertThat(chargeCardDetails.get("address_line1"), is(notNullValue()));
        assertThat(chargeCardDetails.get("address_line2"), is(notNullValue()));
        assertThat(chargeCardDetails.get("address_postcode"), is(notNullValue()));
        assertThat(chargeCardDetails.get("address_country"), is(notNullValue()));
    }

    private void captureAndVerifyFor(String chargeId, int expectedStatusCode, String message) {
        givenSetup()
                .post(captureChargeUrlFor(chargeId))
                .then()
                .statusCode(expectedStatusCode)
                .contentType(JSON)
                .body("message", contains(message))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

}
