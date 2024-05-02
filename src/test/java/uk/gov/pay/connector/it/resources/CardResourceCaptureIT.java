package uk.gov.pay.connector.it.resources;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.it.util.ChargeUtils;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.util.Map;

import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_ERROR_GATEWAY;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_SUCCESS;

public class CardResourceCaptureIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("sandbox", app.getLocalPort(), app.getDatabaseTestHelper());

    @Test
    void shouldFailPayment_IfCaptureStatusIsUnknown() {
        String failedChargeId = testBaseExtension.createNewCharge(CAPTURE_ERROR);
        testBaseExtension.assertApiStateIs(failedChargeId, EXTERNAL_ERROR_GATEWAY.getStatus());
    }

    @Test
    void shouldSubmitForCaptureTheCardPayment_IfChargeWasPreviouslyAuthorised() {
        String chargeId = testBaseExtension.authoriseNewCharge();
        app.givenSetup()
                .post(ITestBaseExtension.captureChargeUrlFor(chargeId))
                .then()
                .statusCode(204);

        testBaseExtension.assertFrontendChargeStatusIs(chargeId, CAPTURE_APPROVED.getValue());
        testBaseExtension.assertApiStateIs(chargeId, EXTERNAL_SUCCESS.getStatus());
    }

    @Test
    void shouldReturn404IfChargeDoesNotExist_ForCapture() {
        String unknownId = "398579438759438";
        String message = String.format("Charge with id [%s] not found.", unknownId);

        captureAndVerifyFor(unknownId, 404, message);
    }

    @Test
    void shouldReturnErrorWithoutChangingChargeState_IfChargeIsCaptureReady() {
        String chargeId = testBaseExtension.createNewChargeWithNoTransactionId(CAPTURE_READY);
        String message = "Charge not in correct state to be processed, " + chargeId;
        captureAndVerifyFor(chargeId, 400, message);

        testBaseExtension.assertFrontendChargeStatusIs(chargeId, CAPTURE_READY.getValue());
    }

    @Test
    void shouldReturnErrorWithoutChangingChargeState_IfChargeIsExpired() {
        String chargeId = testBaseExtension.createNewChargeWithNoTransactionId(EXPIRED);
        String message = format("Charge not in correct state to be processed, %s", chargeId);
        captureAndVerifyFor(chargeId, 400, message);
        testBaseExtension.assertFrontendChargeStatusIs(chargeId, EXPIRED.getValue());
    }

    @Test
    void shouldPreserveCardDetails_IfCaptureReady() {
        String externalChargeId = testBaseExtension.authoriseNewCharge();
        Long chargeId = Long.valueOf(StringUtils.removeStart(externalChargeId, "charge-"));

        Map<String, Object> chargeCardDetails = app.getDatabaseTestHelper().getChargeCardDetailsByChargeId(chargeId);
        assertThat(chargeCardDetails.isEmpty(), is(false));

        app.givenSetup()
                .post(ITestBaseExtension.captureChargeUrlFor(externalChargeId))
                .then()
                .statusCode(204);

        chargeCardDetails = app.getDatabaseTestHelper().getChargeCardDetailsByChargeId(chargeId);
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

    @Test
    void shouldSetPaymentInstrumentOnAgreementAndCancelPreviousPaymentInstruments_forSetUpAgreementCharge() {
        String agreementExternalId = testBaseExtension.addAgreement();
        Long previousPaymentInstrumentId = testBaseExtension.addPaymentInstrument(agreementExternalId, PaymentInstrumentStatus.ACTIVE);
        Long newPaymentInstrumentId = testBaseExtension.addPaymentInstrument(null, PaymentInstrumentStatus.CREATED);
        ChargeUtils.ExternalChargeId externalChargeId = testBaseExtension.addChargeForSetUpAgreement(AUTHORISATION_SUCCESS, agreementExternalId, newPaymentInstrumentId);

        app.givenSetup()
                .post(ITestBaseExtension.captureChargeUrlFor(externalChargeId.toString()))
                .then()
                .statusCode(204);

        Map<String, Object> previousPaymentInstrument = app.getDatabaseTestHelper().getPaymentInstrument(previousPaymentInstrumentId);
        assertThat(previousPaymentInstrument.get("status"), is("CANCELLED"));

        Map<String, Object> newPaymentInstrument = app.getDatabaseTestHelper().getPaymentInstrument(newPaymentInstrumentId);
        assertThat(newPaymentInstrument.get("status"), is("ACTIVE"));
        assertThat(newPaymentInstrument.get("agreement_external_id"), is(agreementExternalId));

        Map<String, Object> agreement = app.getDatabaseTestHelper().getAgreementByExternalId(agreementExternalId);
        assertThat(agreement.get("payment_instrument_id"), is(newPaymentInstrumentId));
    }

    private void captureAndVerifyFor(String chargeId, int expectedStatusCode, String message) {
        app.givenSetup()
                .post(ITestBaseExtension.captureChargeUrlFor(chargeId))
                .then()
                .statusCode(expectedStatusCode)
                .contentType(JSON)
                .body("message", contains(message))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }
}
