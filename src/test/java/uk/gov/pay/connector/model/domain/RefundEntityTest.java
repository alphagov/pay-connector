package uk.gov.pay.connector.model.domain;

import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;
import static uk.gov.pay.connector.model.domain.RefundStatus.*;

public class RefundEntityTest {

    @Test
    public void shouldConstructANewEntity() {
        ChargeEntity chargeEntity = aValidChargeEntity().build();
        Long amount = 100L;

        RefundEntity refundEntity = new RefundEntity(chargeEntity, amount);

        assertNotNull(refundEntity.getExternalId());
        assertThat(refundEntity.getChargeEntity(), is(chargeEntity));
        assertThat(refundEntity.getAmount(), is(amount));
        assertThat(refundEntity.getStatus(), is(RefundStatus.CREATED));
        assertNotNull(refundEntity.getCreatedDate());
    }

    @Test
    public void shouldHaveTheGivenStatus() {
        assertTrue(aValidRefundEntity().withStatus(CREATED).build().hasStatus(CREATED));
        assertTrue(aValidRefundEntity().withStatus(REFUNDED).build().hasStatus(REFUNDED));
    }

    @Test
    public void shouldHaveAtLeastOneOfTheGivenStatuses() {
        assertTrue(aValidRefundEntity().withStatus(CREATED).build().hasStatus(CREATED, REFUNDED));
        assertTrue(aValidRefundEntity().withStatus(REFUNDED).build().hasStatus(CREATED, REFUNDED));
    }

    @Test
    public void shouldHaveNoneOfTheGivenStatuses() {
        assertFalse(aValidRefundEntity().withStatus(CREATED).build().hasStatus(REFUND_SUBMITTED, REFUNDED));
    }

}
