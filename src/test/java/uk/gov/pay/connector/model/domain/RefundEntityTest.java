package uk.gov.pay.connector.model.domain;

import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userExternalId;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.CREATED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_SUBMITTED;

public class RefundEntityTest {

    @Test
    public void shouldConstructANewEntity() {
        ChargeEntity chargeEntity = aValidChargeEntity().build();
        Long amount = 100L;

        RefundEntity refundEntity = new RefundEntity(chargeEntity, amount, userExternalId);
        refundEntity.setStatus(CREATED);

        assertNotNull(refundEntity.getExternalId());
        assertThat(refundEntity.getChargeEntity(), is(chargeEntity));
        assertThat(refundEntity.getReference(), is(nullValue()));
        assertThat(refundEntity.getAmount(), is(amount));
        assertThat(refundEntity.getStatus(), is(CREATED));
        assertThat(refundEntity.getUserExternalId(), is(userExternalId));
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
