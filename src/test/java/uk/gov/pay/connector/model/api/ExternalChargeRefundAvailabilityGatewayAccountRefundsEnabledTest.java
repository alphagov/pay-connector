package uk.gov.pay.connector.model.api;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.model.domain.RefundStatus;

import java.util.Arrays;
import java.util.Collection;

import static com.google.common.collect.Maps.newHashMap;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability.*;
import static uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability.valueOf;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;

@RunWith(Parameterized.class)
public class ExternalChargeRefundAvailabilityGatewayAccountRefundsEnabledTest {

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {new GatewayAccountEntity("worldpay", newHashMap(), Type.TEST)},
                {new GatewayAccountEntity("sandbox", newHashMap(), Type.TEST)}
        });
    }

    private GatewayAccountEntity gatewayAccount;

    public ExternalChargeRefundAvailabilityGatewayAccountRefundsEnabledTest(GatewayAccountEntity gatewayAccount) {
        this.gatewayAccount = gatewayAccount;
    }

    @Test
    public void testGetChargeRefundAvailabilityReturnsPending() {
        assertEquals(EXTERNAL_PENDING, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(CREATED).build()));
        assertEquals(EXTERNAL_PENDING, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(ENTERING_CARD_DETAILS).build()));
        assertEquals(EXTERNAL_PENDING, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(AUTHORISATION_READY).build()));
        assertEquals(EXTERNAL_PENDING, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(AUTHORISATION_SUCCESS).build()));
        assertEquals(EXTERNAL_PENDING, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(CAPTURE_READY).build()));
        assertEquals(EXTERNAL_PENDING, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(CAPTURE_SUBMITTED).build()));
    }

    @Test
    public void testGetChargeRefundAvailabilityReturnsUnavailable() {
        assertEquals(EXTERNAL_UNAVAILABLE, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(AUTHORISATION_REJECTED).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(AUTHORISATION_ERROR).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(EXPIRED).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(CAPTURE_ERROR).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(EXPIRE_CANCEL_READY).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(EXPIRE_CANCEL_FAILED).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(SYSTEM_CANCEL_READY).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(SYSTEM_CANCEL_ERROR).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(SYSTEM_CANCELLED).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(USER_CANCEL_READY).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(USER_CANCELLED).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
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
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(CAPTURED)
                .withAmount(500L)
                .withRefunds(Arrays.asList(refunds))
                .build()));
    }

    @Test
    public void shouldGetChargeRefundAvailabilityAsUnavailable_whenChargeStatusIsInANonRefundableState() {

        assertThat(ExternalChargeRefundAvailability.valueOf(aValidChargeEntity()
                .withStatus(EXPIRED)
                .withAmount(500L)
                .withGatewayAccountEntity(gatewayAccount)
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
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(CAPTURED)
                .withAmount(500L)
                .withRefunds(Arrays.asList(refunds))
                .build()));
    }
}
