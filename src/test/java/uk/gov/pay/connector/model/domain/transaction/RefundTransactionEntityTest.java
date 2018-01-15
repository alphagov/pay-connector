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

    @Test
    public void setStatusAddsTransactionEvent() throws Exception {
        RefundTransactionEntity refundTransactionEntity = new RefundTransactionEntity();
        RefundStatus expectedStatus = RefundStatus.CREATED;

        assertThat(refundTransactionEntity.getTransactionEvents().size(), is(0));
        refundTransactionEntity.updateStatus(expectedStatus);

        List<RefundTransactionEventEntity> transactionEvents = refundTransactionEntity.getTransactionEvents();
        assertThat(transactionEvents.size(), is(1));
        assertThat(transactionEvents.get(0).getStatus(), is(expectedStatus));
    }

    @Test
    public void setMultipleStatusesAddsMultipleTransactionEvent() throws Exception {
        RefundTransactionEntity refundTransactionEntity = new RefundTransactionEntity();

        RefundStatus expectedStatus1 = RefundStatus.CREATED;
        refundTransactionEntity.updateStatus(expectedStatus1);
        RefundStatus expectedStatus2 = RefundStatus.REFUND_SUBMITTED;
        refundTransactionEntity.updateStatus(expectedStatus2);

        List<RefundTransactionEventEntity> transactionEvents = refundTransactionEntity.getTransactionEvents();
        assertThat(transactionEvents.size(), is(2));
        assertThat(transactionEvents.get(0).getStatus(), is(expectedStatus2));
        assertThat(transactionEvents.get(1).getStatus(), is(expectedStatus1));
    }
}
