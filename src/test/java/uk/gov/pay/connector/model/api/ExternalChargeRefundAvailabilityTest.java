package uk.gov.pay.connector.model.api;

import org.junit.Test;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.model.domain.RefundStatus;

import java.util.Arrays;

import static com.google.common.collect.Maps.newHashMap;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability.*;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.*;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;

public class ExternalChargeRefundAvailabilityTest {

    private GatewayAccountEntity worldpayGateway = new GatewayAccountEntity("worldpay", newHashMap(), Type.TEST);

    @Test
    public void testGetChargeRefundAvailabilityReturnsPending() {
        assertEquals(EXTERNAL_PENDING, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(worldpayGateway)
                .withStatus(CREATED).build()));
        assertEquals(EXTERNAL_PENDING, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(worldpayGateway)
                .withStatus(ENTERING_CARD_DETAILS).build()));
        assertEquals(EXTERNAL_PENDING, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(worldpayGateway)
                .withStatus(AUTHORISATION_READY).build()));
        assertEquals(EXTERNAL_PENDING, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(worldpayGateway)
                .withStatus(AUTHORISATION_SUCCESS).build()));
        assertEquals(EXTERNAL_PENDING, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(worldpayGateway)
                .withStatus(CAPTURE_READY).build()));
        assertEquals(EXTERNAL_PENDING, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(worldpayGateway)
                .withStatus(CAPTURE_SUBMITTED).build()));
    }

    @Test
    public void testGetChargeRefundAvailabilityReturnsUnavailable() {
        assertEquals(EXTERNAL_UNAVAILABLE, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(worldpayGateway)
                .withStatus(AUTHORISATION_REJECTED).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(worldpayGateway)
                .withStatus(AUTHORISATION_ERROR).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(worldpayGateway)
                .withStatus(EXPIRED).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(worldpayGateway)
                .withStatus(CAPTURE_ERROR).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(worldpayGateway)
                .withStatus(EXPIRE_CANCEL_READY).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(worldpayGateway)
                .withStatus(EXPIRE_CANCEL_FAILED).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(worldpayGateway)
                .withStatus(SYSTEM_CANCEL_READY).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(worldpayGateway)
                .withStatus(SYSTEM_CANCEL_ERROR).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(worldpayGateway)
                .withStatus(SYSTEM_CANCELLED).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(worldpayGateway)
                .withStatus(USER_CANCEL_READY).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(worldpayGateway)
                .withStatus(USER_CANCELLED).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(worldpayGateway)
                .withStatus(USER_CANCEL_ERROR).build()));
    }

    @Test
    public void testGetChargeRefundAvailabilityReturnsAvailable() {

        RefundEntity[] refunds = new RefundEntity[]{
                aValidRefundEntity().withStatus(RefundStatus.CREATED).withAmount(100L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUND_SUBMITTED).withAmount(200L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUND_ERROR).withAmount(100L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUNDED).withAmount(199L).build()
        };

        assertEquals(EXTERNAL_AVAILABLE, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(worldpayGateway)
                .withStatus(CAPTURED)
                .withAmount(500L)
                .withRefunds(Arrays.asList(refunds))
                .build()));
    }

    @Test
    public void shouldGetChargeRefundAvailabilityAsUnavailable_whenGatewayProviderIsSandbox() {

        GatewayAccountEntity sandboxGateway = new GatewayAccountEntity("sandbox", newHashMap(), Type.TEST);

        assertThat(ExternalChargeRefundAvailability.valueOf(aValidChargeEntity()
                .withStatus(CAPTURED)
                .withAmount(500L)
                .withGatewayAccountEntity(sandboxGateway)
                .build()), is(EXTERNAL_UNAVAILABLE));

    }

    @Test
    public void shouldGetChargeRefundAvailabilityAsUnavailable_whenGatewayProviderIsSmartpay() {

        GatewayAccountEntity smartpayGateway = new GatewayAccountEntity("smartpay", newHashMap(), Type.TEST);

        assertThat(ExternalChargeRefundAvailability.valueOf(aValidChargeEntity()
                .withStatus(CAPTURED)
                .withAmount(500L)
                .withGatewayAccountEntity(smartpayGateway)
                .build()), is(EXTERNAL_UNAVAILABLE));
    }

    @Test
    public void testGetChargeRefundAvailabilityReturnsFull() {
        RefundEntity[] refunds = new RefundEntity[]{
                aValidRefundEntity().withStatus(RefundStatus.CREATED).withAmount(100L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUND_SUBMITTED).withAmount(200L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUNDED).withAmount(200L).build()
        };

        assertEquals(EXTERNAL_FULL, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(worldpayGateway)
                .withStatus(CAPTURED)
                .withAmount(500L)
                .withRefunds(Arrays.asList(refunds))
                .build()));
    }
}
