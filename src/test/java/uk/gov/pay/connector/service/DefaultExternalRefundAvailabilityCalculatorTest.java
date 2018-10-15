package uk.gov.pay.connector.service;

import org.junit.Test;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.gateway.util.DefaultExternalRefundAvailabilityCalculator;

import java.util.Arrays;
import java.util.List;

import static com.google.common.collect.Maps.newHashMap;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability.EXTERNAL_AVAILABLE;
import static uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability.EXTERNAL_FULL;
import static uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability.EXTERNAL_PENDING;
import static uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability.EXTERNAL_UNAVAILABLE;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.EXPIRED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.EXPIRE_CANCEL_FAILED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.EXPIRE_CANCEL_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.SYSTEM_CANCELLED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.SYSTEM_CANCEL_ERROR;
import static uk.gov.pay.connector.model.domain.ChargeStatus.SYSTEM_CANCEL_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.USER_CANCELLED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.USER_CANCEL_ERROR;
import static uk.gov.pay.connector.model.domain.ChargeStatus.USER_CANCEL_READY;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;

public class DefaultExternalRefundAvailabilityCalculatorTest {

    private final DefaultExternalRefundAvailabilityCalculator defaultExternalRefundAvailabilityCalculator = new DefaultExternalRefundAvailabilityCalculator();

    @Test
    public void testGetChargeRefundAvailabilityReturnsPending() {
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(CREATED)), is(EXTERNAL_PENDING));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(ENTERING_CARD_DETAILS)), is(EXTERNAL_PENDING));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(AUTHORISATION_READY)), is(EXTERNAL_PENDING));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(AUTHORISATION_3DS_REQUIRED)), is(EXTERNAL_PENDING));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(AUTHORISATION_3DS_READY)), is(EXTERNAL_PENDING));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(AUTHORISATION_SUBMITTED)), is(EXTERNAL_PENDING));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(AUTHORISATION_SUCCESS)), is(EXTERNAL_PENDING));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(CAPTURE_READY)), is(EXTERNAL_PENDING));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(CAPTURE_APPROVED)), is(EXTERNAL_PENDING));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(CAPTURE_APPROVED_RETRY)), is(EXTERNAL_PENDING));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(CAPTURE_SUBMITTED)), is(EXTERNAL_PENDING));
    }

    @Test
    public void testGetChargeRefundAvailabilityReturnsUnavailable() {
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(AUTHORISATION_REJECTED)), is(EXTERNAL_UNAVAILABLE));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(AUTHORISATION_ERROR)), is(EXTERNAL_UNAVAILABLE));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(EXPIRED)), is(EXTERNAL_UNAVAILABLE));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(CAPTURE_ERROR)), is(EXTERNAL_UNAVAILABLE));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(EXPIRE_CANCEL_READY)), is(EXTERNAL_UNAVAILABLE));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(EXPIRE_CANCEL_FAILED)), is(EXTERNAL_UNAVAILABLE));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(SYSTEM_CANCEL_READY)), is(EXTERNAL_UNAVAILABLE));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(SYSTEM_CANCEL_ERROR)), is(EXTERNAL_UNAVAILABLE));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(SYSTEM_CANCELLED)), is(EXTERNAL_UNAVAILABLE));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(USER_CANCEL_READY)), is(EXTERNAL_UNAVAILABLE));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(USER_CANCELLED)), is(EXTERNAL_UNAVAILABLE));
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(USER_CANCEL_ERROR)), is(EXTERNAL_UNAVAILABLE));
    }

    @Test
    public void testGetChargeRefundAvailabilityReturnsAvailable() {
        List<RefundEntity> refunds = Arrays.asList(
                aValidRefundEntity().withStatus(RefundStatus.CREATED).withAmount(100L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUND_SUBMITTED).withAmount(200L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUND_ERROR).withAmount(100L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUNDED).withAmount(199L).build()
        );

        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(CAPTURED, 500L, refunds)), is(EXTERNAL_AVAILABLE));
    }

    @Test
    public void shouldGetChargeRefundAvailabilityAsUnavailable_whenChargeStatusIsInANonRefundableState() {
        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(EXPIRED, 500L)), is(EXTERNAL_UNAVAILABLE));
    }

    @Test
    public void testGetChargeRefundAvailabilityReturnsFull() {
        List<RefundEntity> refunds = Arrays.asList(
                aValidRefundEntity().withStatus(RefundStatus.CREATED).withAmount(100L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUND_SUBMITTED).withAmount(200L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUNDED).withAmount(200L).build()
        );

        assertThat(defaultExternalRefundAvailabilityCalculator.calculate(chargeEntity(CAPTURED, 500L, refunds)), is(EXTERNAL_FULL));

    }

    private static ChargeEntity chargeEntity(ChargeStatus status) {
        GatewayAccountEntity gatewayAccountEntity = new GatewayAccountEntity("sandbox", newHashMap(), GatewayAccountEntity.Type.TEST);
        return aValidChargeEntity().withGatewayAccountEntity(gatewayAccountEntity).withStatus(status).build();
    }

    private static ChargeEntity chargeEntity(ChargeStatus status, long amount) {
        GatewayAccountEntity gatewayAccountEntity = new GatewayAccountEntity("sandbox", newHashMap(), GatewayAccountEntity.Type.TEST);
        return aValidChargeEntity().withGatewayAccountEntity(gatewayAccountEntity).withStatus(status).withAmount(amount).build();
    }

    private static ChargeEntity chargeEntity(ChargeStatus status, long amount, List<RefundEntity> refunds) {
        GatewayAccountEntity gatewayAccountEntity = new GatewayAccountEntity("sandbox", newHashMap(), GatewayAccountEntity.Type.TEST);
        return aValidChargeEntity().withGatewayAccountEntity(gatewayAccountEntity).withStatus(status).withAmount(amount).withRefunds(refunds).build();
    }

}
