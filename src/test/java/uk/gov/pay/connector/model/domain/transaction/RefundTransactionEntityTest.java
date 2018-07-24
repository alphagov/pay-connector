package uk.gov.pay.connector.model.domain.transaction;

import org.junit.Test;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.model.domain.RefundStatus;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class RefundTransactionEntityTest {
    @Test
    public void createsARefundTransactionEntityFromRefundEntity() throws Exception {
        long amount = 123L;
        String externalId = "someExternalId";
        String refundReference = "refundReference";
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity()
                .withAmount(amount)
                .withExternalId(externalId)
                .withStatus(RefundStatus.REFUND_ERROR)
                .withReference(refundReference)
                .build();

        RefundTransactionEntity transactionEntity = RefundTransactionEntity.from(refundEntity);

        assertThat(transactionEntity.getAmount(), is(amount));
        assertThat(transactionEntity.getRefundExternalId(), is(externalId));
        assertThat(transactionEntity.getUserExternalId(), is(RefundEntityFixture.userExternalId));
        assertThat(transactionEntity.getStatus(), is(RefundStatus.REFUND_ERROR));
        assertThat(refundEntity.getCreatedDate(), is(transactionEntity.getCreatedDate()));
    }



}
