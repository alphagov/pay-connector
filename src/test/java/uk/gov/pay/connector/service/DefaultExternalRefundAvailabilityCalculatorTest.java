package uk.gov.pay.connector.service;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.util.DefaultExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

class DefaultExternalRefundAvailabilityCalculatorTest {

    private final DefaultExternalRefundAvailabilityCalculator defaultExternalRefundAvailabilityCalculator = new DefaultExternalRefundAvailabilityCalculator();

    @Test
    void testGetChargeRefundAvailabilityReturnsPending() {
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
    void testGetChargeRefundAvailabilityReturnsUnavailable() {
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
    void testGetChargeRefundAvailabilityReturnsAvailable() {
        List<Refund> refunds = Stream.of(
                aValidRefundEntity().withStatus(RefundStatus.CREATED).withAmount(100L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUND_SUBMITTED).withAmount(200L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUND_ERROR).withAmount(100L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUNDED).withAmount(199L).build()
        ).map(Refund::from).collect(Collectors.toList());

        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(CAPTURED, 500L), refunds), is(EXTERNAL_AVAILABLE));
    }

    @Test
    void shouldGetChargeRefundAvailabilityAsUnavailable_whenChargeStatusIsInANonRefundableState() {
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(EXPIRED, 500L), List.of()), is(EXTERNAL_UNAVAILABLE));
    }

    @Test
    void testGetChargeRefundAvailabilityReturnsFull() {
        List<Refund> refunds = Stream.of(
                aValidRefundEntity().withStatus(RefundStatus.CREATED).withAmount(100L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUND_SUBMITTED).withAmount(200L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUNDED).withAmount(200L).build()
        ).map(Refund::from).collect(Collectors.toList());

        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(CAPTURED, 500L), refunds), is(EXTERNAL_FULL));
    }

    @Test
    void historicCharge_shouldRecalculateAvailabilityIfCurrentAvailabilityIsMutable() {
        List<Refund> fullRefunds = Stream.of(
                aValidRefundEntity().withStatus(RefundStatus.CREATED).withAmount(100L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUND_SUBMITTED).withAmount(200L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUNDED).withAmount(200L).build()
        ).map(Refund::from).collect(Collectors.toList());
        List<Refund> previouslyFullNowErroredRefunds = Stream.of(
                aValidRefundEntity().withStatus(RefundStatus.CREATED).withAmount(100L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUND_SUBMITTED).withAmount(200L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUND_ERROR).withAmount(200L).build()
        ).map(Refund::from).collect(Collectors.toList());

        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(getHistoricCharge(EXTERNAL_AVAILABLE), fullRefunds), is(EXTERNAL_FULL));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(getHistoricCharge(EXTERNAL_FULL), previouslyFullNowErroredRefunds), is(EXTERNAL_AVAILABLE));
    }

    @Test
    void historicCharge_shouldNotRecalculateAvailabilityIfCurrentAvailabilityIsTerminal() {
        List<Refund> refunds = Arrays.asList(
                Refund.from(aValidRefundEntity().withStatus(RefundStatus.CREATED).withAmount(100L).build())
        );

        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(getHistoricCharge(EXTERNAL_UNAVAILABLE), List.of()), is(EXTERNAL_UNAVAILABLE));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(getHistoricCharge(EXTERNAL_PENDING), refunds), is(EXTERNAL_PENDING));
    }

    private Charge chargeEntity(ChargeStatus status) {
        GatewayAccountEntity gatewayAccountEntity = new GatewayAccountEntity(GatewayAccountType.TEST);
        return Charge.from(
                aValidChargeEntity().withGatewayAccountEntity(gatewayAccountEntity).withStatus(status).build()
        );
    }

    private Charge chargeEntity(ChargeStatus status, long amount) {
        GatewayAccountEntity gatewayAccountEntity = new GatewayAccountEntity(GatewayAccountType.TEST);
        return Charge.from(
                aValidChargeEntity().withGatewayAccountEntity(gatewayAccountEntity).withStatus(status).withAmount(amount).build()
        );
    }

    private Charge getHistoricCharge(ExternalChargeRefundAvailability availability) {
        return new Charge("external-id", 500L, null, "success", "transaction-id",
                "credentials_external_id", 0L, availability.getStatus(), "ref-1", "desc", Instant.now(),
                "test@example.org", 123L, "epdq", true, "service-id", true, false, AuthorisationMode.WEB,
                null);
    }
}
