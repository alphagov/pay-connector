package uk.gov.pay.connector.model.api;

import org.junit.Test;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.model.domain.RefundStatus;

import java.util.Arrays;

import static org.junit.Assert.*;
import static uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability.*;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;

public class ExternalChargeRefundAvailabilityTest {

    @Test
    public void testGetChargeRefundAvailabilityReturnsPending() {
        assertEquals(EXTERNAL_PENDING, ExternalChargeRefundAvailability.valueOf(aValidChargeEntity()
                .withStatus(CREATED).build()));
        assertEquals(EXTERNAL_PENDING, ExternalChargeRefundAvailability.valueOf(aValidChargeEntity()
                .withStatus(ENTERING_CARD_DETAILS).build()));
        assertEquals(EXTERNAL_PENDING, ExternalChargeRefundAvailability.valueOf(aValidChargeEntity()
                .withStatus(AUTHORISATION_READY).build()));
        assertEquals(EXTERNAL_PENDING, ExternalChargeRefundAvailability.valueOf(aValidChargeEntity()
                .withStatus(AUTHORISATION_SUCCESS).build()));
        assertEquals(EXTERNAL_PENDING, ExternalChargeRefundAvailability.valueOf(aValidChargeEntity()
                .withStatus(CAPTURE_READY).build()));
        assertEquals(EXTERNAL_PENDING, ExternalChargeRefundAvailability.valueOf(aValidChargeEntity()
                .withStatus(CAPTURE_SUBMITTED).build()));
    }

    @Test
    public void testGetChargeRefundAvailabilityReturnsUnavailable() {
        assertEquals(EXTERNAL_UNAVAILABLE, ExternalChargeRefundAvailability.valueOf(aValidChargeEntity()
                .withStatus(AUTHORISATION_REJECTED).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, ExternalChargeRefundAvailability.valueOf(aValidChargeEntity()
                .withStatus(AUTHORISATION_ERROR).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, ExternalChargeRefundAvailability.valueOf(aValidChargeEntity()
                .withStatus(EXPIRED).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, ExternalChargeRefundAvailability.valueOf(aValidChargeEntity()
                .withStatus(CAPTURE_ERROR).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, ExternalChargeRefundAvailability.valueOf(aValidChargeEntity()
                .withStatus(EXPIRE_CANCEL_READY).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, ExternalChargeRefundAvailability.valueOf(aValidChargeEntity()
                .withStatus(EXPIRE_CANCEL_FAILED).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, ExternalChargeRefundAvailability.valueOf(aValidChargeEntity()
                .withStatus(SYSTEM_CANCEL_READY).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, ExternalChargeRefundAvailability.valueOf(aValidChargeEntity()
                .withStatus(SYSTEM_CANCEL_ERROR).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, ExternalChargeRefundAvailability.valueOf(aValidChargeEntity()
                .withStatus(SYSTEM_CANCELLED).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, ExternalChargeRefundAvailability.valueOf(aValidChargeEntity()
                .withStatus(USER_CANCEL_READY).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, ExternalChargeRefundAvailability.valueOf(aValidChargeEntity()
                .withStatus(USER_CANCELLED).build()));
        assertEquals(EXTERNAL_UNAVAILABLE, ExternalChargeRefundAvailability.valueOf(aValidChargeEntity()
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

        assertEquals(EXTERNAL_AVAILABLE, ExternalChargeRefundAvailability.valueOf(aValidChargeEntity()
                .withStatus(CAPTURED)
                .withAmount(500L)
                .withRefunds(Arrays.asList(refunds))
                .build()));
    }

    @Test
    public void testGetChargeRefundAvailabilityReturnsFull() {
        RefundEntity[] refunds = new RefundEntity[]{
                aValidRefundEntity().withStatus(RefundStatus.CREATED).withAmount(100L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUND_SUBMITTED).withAmount(200L).build(),
                aValidRefundEntity().withStatus(RefundStatus.REFUNDED).withAmount(200L).build()
        };

        assertEquals(EXTERNAL_FULL, ExternalChargeRefundAvailability.valueOf(aValidChargeEntity()
                .withStatus(CAPTURED)
                .withAmount(500L)
                .withRefunds(Arrays.asList(refunds))
                .build()));
    }
}
