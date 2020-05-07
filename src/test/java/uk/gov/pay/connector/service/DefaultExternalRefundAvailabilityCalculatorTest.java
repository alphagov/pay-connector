package uk.gov.pay.connector.service;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.util.DefaultExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import java.util.Arrays;
import java.util.List;

import static com.google.common.collect.Maps.newHashMap;
import static java.time.ZonedDateTime.now;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRE_CANCEL_FAILED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRE_CANCEL_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCEL_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCEL_READY;
import static uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability.EXTERNAL_AVAILABLE;
import static uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability.EXTERNAL_FULL;
import static uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability.EXTERNAL_PENDING;
import static uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability.EXTERNAL_UNAVAILABLE;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;

@RunWith(JUnitParamsRunner.class)
public class DefaultExternalRefundAvailabilityCalculatorTest {

    private final DefaultExternalRefundAvailabilityCalculator defaultExternalRefundAvailabilityCalculator = new DefaultExternalRefundAvailabilityCalculator();

    @Test
    public void testGetChargeRefundAvailabilityReturnsPending() {
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(CREATED), List.of()), is(EXTERNAL_PENDING));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(ENTERING_CARD_DETAILS), List.of()), is(EXTERNAL_PENDING));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(AUTHORISATION_READY), List.of()), is(EXTERNAL_PENDING));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(AUTHORISATION_3DS_REQUIRED), List.of()), is(EXTERNAL_PENDING));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(AUTHORISATION_3DS_READY), List.of()), is(EXTERNAL_PENDING));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(AUTHORISATION_SUBMITTED), List.of()), is(EXTERNAL_PENDING));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(AUTHORISATION_SUCCESS), List.of()), is(EXTERNAL_PENDING));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(CAPTURE_READY), List.of()), is(EXTERNAL_PENDING));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(CAPTURE_APPROVED), List.of()), is(EXTERNAL_PENDING));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(CAPTURE_APPROVED_RETRY), List.of()), is(EXTERNAL_PENDING));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(CAPTURE_SUBMITTED), List.of()), is(EXTERNAL_PENDING));
    }

    @Test
    public void testGetChargeRefundAvailabilityReturnsUnavailable() {
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(AUTHORISATION_REJECTED), List.of()), is(EXTERNAL_UNAVAILABLE));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(AUTHORISATION_ERROR), List.of()), is(EXTERNAL_UNAVAILABLE));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(EXPIRED), List.of()), is(EXTERNAL_UNAVAILABLE));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(CAPTURE_ERROR), List.of()), is(EXTERNAL_UNAVAILABLE));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(EXPIRE_CANCEL_READY), List.of()), is(EXTERNAL_UNAVAILABLE));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(EXPIRE_CANCEL_FAILED), List.of()), is(EXTERNAL_UNAVAILABLE));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(SYSTEM_CANCEL_READY), List.of()), is(EXTERNAL_UNAVAILABLE));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(SYSTEM_CANCEL_ERROR), List.of()), is(EXTERNAL_UNAVAILABLE));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(SYSTEM_CANCELLED), List.of()), is(EXTERNAL_UNAVAILABLE));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(USER_CANCEL_READY), List.of()), is(EXTERNAL_UNAVAILABLE));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(USER_CANCELLED), List.of()), is(EXTERNAL_UNAVAILABLE));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(USER_CANCEL_ERROR), List.of()), is(EXTERNAL_UNAVAILABLE));
    }

    @Test
    public void testGetChargeRefundAvailabilityReturnsAvailable() {
        List<RefundEntity> refunds = Arrays.asList(
                aValidRefundEntity().withStatus(RefundStatus.CREATED).withAmount(100L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUND_SUBMITTED).withAmount(200L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUND_ERROR).withAmount(100L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUNDED).withAmount(199L).build()
        );

        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(CAPTURED, 500L), refunds), is(EXTERNAL_AVAILABLE));
    }

    @Test
    public void shouldGetChargeRefundAvailabilityAsUnavailable_whenChargeStatusIsInANonRefundableState() {
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(EXPIRED, 500L), List.of()), is(EXTERNAL_UNAVAILABLE));
    }

    @Test
    public void testGetChargeRefundAvailabilityReturnsFull() {
        List<RefundEntity> refunds = Arrays.asList(
                aValidRefundEntity().withStatus(RefundStatus.CREATED).withAmount(100L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUND_SUBMITTED).withAmount(200L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUNDED).withAmount(200L).build()
        );

        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(CAPTURED, 500L), refunds), is(EXTERNAL_FULL));

    }

    @Test
    @Parameters({"created", "started", "submitted", "capturable", "success"})
    public void shouldReturnRefundAvailabilityAsPendingIfChargeIsHistoricButNotRefundable(String status) {
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(
                getCharge(true, status, null, null),
                List.of()), is(EXTERNAL_PENDING));
    }

    @Test
    public void shouldReturnRefundAvailabilityAsAvailableIfChargeIsHistoricAndCaptured() {
        List<RefundEntity> refunds = Arrays.asList(
                aValidRefundEntity().withStatus(RefundStatus.CREATED).withAmount(100L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUNDED).withAmount(200L).build()
        );
        // partially refunded charge
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(
                getCharge(true, "success", "2019-11-11", "2019-11-11T13:01:40.844Z"),
                refunds), is(EXTERNAL_AVAILABLE));
        // charge without any refunds
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(
                getCharge(true, "success", "2019-11-11", "2019-11-11T13:01:40.844Z"),
                List.of()), is(EXTERNAL_AVAILABLE));
    }

    @Test
    public void shouldReturnRefundAvailabilityAsFullIfChargeIsHistoricAndFullyRefunded() {
        List<RefundEntity> refunds = Arrays.asList(
                aValidRefundEntity().withStatus(RefundStatus.REFUNDED).withAmount(100L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUNDED).withAmount(400L).build()
        );
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(
                getCharge(true, "success", null, "2019-11-11T13:01:40.844Z"),
                refunds), is(EXTERNAL_FULL));
    }

    @Test
    public void shouldReturnRefundAvailabilityAsAvailableIfChargeIsHistoricAndRefundableAfterCaptureSubmitted() {
        assertThat(defaultExternalRefundAvailabilityCalculator.calculateForHistoricCharge(
                getCharge(true, "success", "2019-11-11", null),
                List.of(), true), is(EXTERNAL_AVAILABLE));
    }

    @Test
    public void shouldReturnRefundAvailabilityAsUnavailableIfChargeIsHistoricAndNotRefundableWhenInCaptureSubmittedState() {
        assertThat(defaultExternalRefundAvailabilityCalculator.calculateForHistoricCharge(
                getCharge(true, "success", "2019-11-11", null),
                List.of(), false), is(EXTERNAL_PENDING));
    }

    @Test
    @Parameters({"declined", "timedout", "cancelled", "error"})
    public void shouldReturnRefundAvailabilityAsUnavailableIfChargeIsHistoricAndFailed(String status) {
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(
                getCharge(true, status, null, null),
                List.of()), is(EXTERNAL_UNAVAILABLE));
    }

    @Test
    @Parameters({"", "unknown", "undefined"})
    public void shouldReturnRefundAvailabilityAsUnavailableIfChargeIsHistoricAndExternalStatusIsUnknown(String status) {
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(
                getCharge(true, status, null, null),
                List.of()), is(EXTERNAL_UNAVAILABLE));
    }

    @Test
    public void shouldReturnRefundAvailabilityAsUnavailableIfChargeIsHistoricAndExternalStatusIsEmpty() {
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(
                getCharge(true, null, null, null),
                List.of()), is(EXTERNAL_UNAVAILABLE));
    }

    private Charge chargeEntity(ChargeStatus status) {
        GatewayAccountEntity gatewayAccountEntity = new GatewayAccountEntity("sandbox", newHashMap(), GatewayAccountEntity.Type.TEST);
        return Charge.from(
                aValidChargeEntity().withGatewayAccountEntity(gatewayAccountEntity).withStatus(status).build()
        );
    }

    private Charge chargeEntity(ChargeStatus status, long amount) {
        GatewayAccountEntity gatewayAccountEntity = new GatewayAccountEntity("sandbox", newHashMap(), GatewayAccountEntity.Type.TEST);
        return Charge.from(
                aValidChargeEntity().withGatewayAccountEntity(gatewayAccountEntity).withStatus(status).withAmount(amount).build()
        );
    }

    private Charge getCharge(boolean isHistoric, String externalStatus, String captureSubmitTime, String capturedDate) {
        return new Charge("external-id", 500L, null, externalStatus, "transaction-id",
                0L, null, "ref-1", "desc", now(),
                "test@example.org", 123L, "epdq", isHistoric, captureSubmitTime, capturedDate);
    }
}
