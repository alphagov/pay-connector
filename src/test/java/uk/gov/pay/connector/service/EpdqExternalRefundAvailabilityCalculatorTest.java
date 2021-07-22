package uk.gov.pay.connector.service;

import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.util.EpdqExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.util.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.collect.Maps.newHashMap;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
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
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;

public class EpdqExternalRefundAvailabilityCalculatorTest {

    private final ExternalRefundAvailabilityCalculator epdqExternalRefundAvailabilityCalculator = new EpdqExternalRefundAvailabilityCalculator();
    
    @Test
    public void testGetChargeRefundAvailabilityReturnsPending() {
        assertThat(epdqExternalRefundAvailabilityCalculator.calculate(chargeEntity(CREATED), List.of()), is(EXTERNAL_PENDING));
        assertThat(epdqExternalRefundAvailabilityCalculator.calculate(chargeEntity(ENTERING_CARD_DETAILS), List.of()), is(EXTERNAL_PENDING));
        assertThat(epdqExternalRefundAvailabilityCalculator.calculate(chargeEntity(AUTHORISATION_READY), List.of()), is(EXTERNAL_PENDING));
        assertThat(epdqExternalRefundAvailabilityCalculator.calculate(chargeEntity(AUTHORISATION_3DS_REQUIRED), List.of()), is(EXTERNAL_PENDING));
        assertThat(epdqExternalRefundAvailabilityCalculator.calculate(chargeEntity(AUTHORISATION_3DS_READY), List.of()), is(EXTERNAL_PENDING));
        assertThat(epdqExternalRefundAvailabilityCalculator.calculate(chargeEntity(AUTHORISATION_SUBMITTED), List.of()), is(EXTERNAL_PENDING));
        assertThat(epdqExternalRefundAvailabilityCalculator.calculate(chargeEntity(AUTHORISATION_SUCCESS), List.of()), is(EXTERNAL_PENDING));
        assertThat(epdqExternalRefundAvailabilityCalculator.calculate(chargeEntity(CAPTURE_READY), List.of()), is(EXTERNAL_PENDING));
        assertThat(epdqExternalRefundAvailabilityCalculator.calculate(chargeEntity(CAPTURE_APPROVED), List.of()), is(EXTERNAL_PENDING));
        assertThat(epdqExternalRefundAvailabilityCalculator.calculate(chargeEntity(CAPTURE_APPROVED_RETRY), List.of()), is(EXTERNAL_PENDING));
    }

    @Test
    public void testGetChargeRefundAvailabilityReturnsUnavailable() {
        assertThat(epdqExternalRefundAvailabilityCalculator.calculate(chargeEntity(AUTHORISATION_REJECTED), List.of()), is(EXTERNAL_UNAVAILABLE));
        assertThat(epdqExternalRefundAvailabilityCalculator.calculate(chargeEntity(AUTHORISATION_ERROR), List.of()), is(EXTERNAL_UNAVAILABLE));
        assertThat(epdqExternalRefundAvailabilityCalculator.calculate(chargeEntity(EXPIRED), List.of()), is(EXTERNAL_UNAVAILABLE));
        assertThat(epdqExternalRefundAvailabilityCalculator.calculate(chargeEntity(CAPTURE_ERROR), List.of()), is(EXTERNAL_UNAVAILABLE));
        assertThat(epdqExternalRefundAvailabilityCalculator.calculate(chargeEntity(EXPIRE_CANCEL_READY), List.of()), is(EXTERNAL_UNAVAILABLE));
        assertThat(epdqExternalRefundAvailabilityCalculator.calculate(chargeEntity(EXPIRE_CANCEL_FAILED), List.of()), is(EXTERNAL_UNAVAILABLE));
        assertThat(epdqExternalRefundAvailabilityCalculator.calculate(chargeEntity(SYSTEM_CANCEL_READY), List.of()), is(EXTERNAL_UNAVAILABLE));
        assertThat(epdqExternalRefundAvailabilityCalculator.calculate(chargeEntity(SYSTEM_CANCEL_ERROR), List.of()), is(EXTERNAL_UNAVAILABLE));
        assertThat(epdqExternalRefundAvailabilityCalculator.calculate(chargeEntity(SYSTEM_CANCELLED), List.of()), is(EXTERNAL_UNAVAILABLE));
        assertThat(epdqExternalRefundAvailabilityCalculator.calculate(chargeEntity(USER_CANCEL_READY), List.of()), is(EXTERNAL_UNAVAILABLE));
        assertThat(epdqExternalRefundAvailabilityCalculator.calculate(chargeEntity(USER_CANCELLED), List.of()), is(EXTERNAL_UNAVAILABLE));
        assertThat(epdqExternalRefundAvailabilityCalculator.calculate(chargeEntity(USER_CANCEL_ERROR), List.of()), is(EXTERNAL_UNAVAILABLE));
    }

    @Test
    public void testGetChargeRefundAvailabilityReturnsAvailableIfChargeIsCaptured() {
        List<Refund> refunds = Stream.of(
                aValidRefundEntity().withStatus(RefundStatus.CREATED).withAmount(100L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUND_SUBMITTED).withAmount(200L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUND_ERROR).withAmount(100L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUNDED).withAmount(199L).build()
        ).map(Refund::from).collect(Collectors.toList());

        assertThat(epdqExternalRefundAvailabilityCalculator.calculate(chargeEntity(CAPTURED, 500L), refunds), is(EXTERNAL_AVAILABLE));
    }

    @Test
    public void testGetChargeRefundAvailabilityReturnsAvailableIfChargeIsCapturedSubmitted() {
        List<Refund> refunds = Stream.of(
                aValidRefundEntity().withStatus(RefundStatus.CREATED).withAmount(100L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUND_SUBMITTED).withAmount(200L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUND_ERROR).withAmount(100L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUNDED).withAmount(199L).build()
        ).map(Refund::from).collect(Collectors.toList());

        assertThat(epdqExternalRefundAvailabilityCalculator.calculate(chargeEntity(CAPTURE_SUBMITTED, 500L), refunds), is(EXTERNAL_AVAILABLE));
    }

    @Test
    public void shouldGetChargeRefundAvailabilityAsUnavailable_whenChargeStatusIsInANonRefundableState() {
        assertThat(epdqExternalRefundAvailabilityCalculator.calculate(chargeEntity(EXPIRED, 500L), List.of()), is(EXTERNAL_UNAVAILABLE));
    }

    @Test
    public void testGetChargeRefundAvailabilityReturnsFullIfChargeIsCaptured() {
        List<Refund> refunds = Stream.of(
                aValidRefundEntity().withStatus(RefundStatus.CREATED).withAmount(100L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUND_SUBMITTED).withAmount(200L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUNDED).withAmount(200L).build()
        ).map(Refund::from).collect(Collectors.toList());

        assertThat(epdqExternalRefundAvailabilityCalculator.calculate(chargeEntity(CAPTURED, 500L), refunds), is(EXTERNAL_FULL));
    }

    @Test
    public void testGetChargeRefundAvailabilityReturnsFullIfChargeIsCapturedSubmitted() {
        List<Refund> refunds = Stream.of(
                aValidRefundEntity().withStatus(RefundStatus.CREATED).withAmount(100L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUND_SUBMITTED).withAmount(200L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUNDED).withAmount(200L).build()
        ).map(Refund::from).collect(Collectors.toList());

        assertThat(epdqExternalRefundAvailabilityCalculator.calculate(chargeEntity(CAPTURE_SUBMITTED, 500L), refunds), is(EXTERNAL_FULL));
    }

    private static Charge chargeEntity(ChargeStatus status) {
        GatewayAccountEntity gatewayAccountEntity = new GatewayAccountEntity(GatewayAccountType.TEST);
        return Charge.from(
                aValidChargeEntity().withGatewayAccountEntity(gatewayAccountEntity).withStatus(status).build()
        );
    }

    private static Charge chargeEntity(ChargeStatus status, long amount) {
        GatewayAccountEntity gatewayAccountEntity = new GatewayAccountEntity(GatewayAccountType.TEST);
        return Charge.from(
                aValidChargeEntity().withGatewayAccountEntity(gatewayAccountEntity).withStatus(status).withAmount(amount).build()
        );
    }
}
