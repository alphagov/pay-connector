package uk.gov.pay.connector.model.domain;


import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userEmail;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userExternalId;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.CREATED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_SUBMITTED;

class RefundEntityTest {

    @Test
    void shouldConstructANewEntity() {
        ChargeEntity chargeEntity = aValidChargeEntity().build();
        Long amount = 100L;

        RefundEntity refundEntity = new RefundEntity(amount, userExternalId, userEmail, chargeEntity.getExternalId());

        assertNotNull(refundEntity.getExternalId());
        assertThat(refundEntity.getGatewayTransactionId(), is(nullValue()));
        assertThat(refundEntity.getAmount(), is(amount));
        assertThat(refundEntity.getUserExternalId(), is(userExternalId));
        assertThat(refundEntity.getChargeExternalId(), is(chargeEntity.getExternalId()));
        assertNotNull(refundEntity.getCreatedDate());
    }

    @Test
    void shouldHaveTheGivenStatus() {
        assertTrue(aValidRefundEntity().withStatus(CREATED).build().hasStatus(CREATED));
        assertTrue(aValidRefundEntity().withStatus(REFUNDED).build().hasStatus(REFUNDED));
    }

    @Test
    void shouldHaveAtLeastOneOfTheGivenStatuses() {
        assertTrue(aValidRefundEntity().withStatus(CREATED).build().hasStatus(CREATED, REFUNDED));
        assertTrue(aValidRefundEntity().withStatus(REFUNDED).build().hasStatus(CREATED, REFUNDED));
    }

    @Test
    void shouldHaveNoneOfTheGivenStatuses() {
        assertFalse(aValidRefundEntity().withStatus(CREATED).build().hasStatus(REFUND_SUBMITTED, REFUNDED));
    }

}
