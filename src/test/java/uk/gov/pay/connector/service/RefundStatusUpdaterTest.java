package uk.gov.pay.connector.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.dao.RefundTransactionDao;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.model.domain.transaction.RefundTransactionEntity;

import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUND_SUBMITTED;
import static uk.gov.pay.connector.model.domain.transaction.RefundTransactionEntityBuilder.aRefundTransactionEntity;

@RunWith(MockitoJUnitRunner.class)
public class RefundStatusUpdaterTest {
    @Mock
    private RefundTransactionDao mockRefundTransactionDao;
    private String someRefundReference;
    private PaymentGatewayName someProvider;
    private RefundStatus newRefundStatus;
    private RefundTransactionEntity refundTransactionEntity;
    private String refundExternalId;

    @Before
    public void setUp() throws Exception {
        someRefundReference = "someRefundReference";
        someProvider = PaymentGatewayName.WORLDPAY;
        newRefundStatus = REFUND_SUBMITTED;
        refundExternalId = "refundExternalId";
        refundTransactionEntity = aRefundTransactionEntity()
                .withRefundExternalId(refundExternalId)
                .withStatus(RefundStatus.CREATED)
                .build();
    }

    @Test
    public void updatesRefundTransactionAndSetsRefundReference() {
        when(mockRefundTransactionDao.findByExternalId(refundExternalId)).thenReturn(Optional.of(refundTransactionEntity));
        new RefundStatusUpdater(mockRefundTransactionDao).setReferenceAndUpdateTransactionStatus(refundExternalId, someRefundReference, newRefundStatus);

        assertThat(refundTransactionEntity.getRefundReference(), is(someRefundReference));
        assertThat(refundTransactionEntity.getStatus(), is(newRefundStatus));

    }

    @Test
    public void updatesRefundTransactionStatusWithDaoLoopkup() throws Exception {
        when(mockRefundTransactionDao.findByProviderAndReference(someProvider, someRefundReference))
                .thenReturn(Optional.of(refundTransactionEntity));

        new RefundStatusUpdater(mockRefundTransactionDao)
                .updateRefundTransactionStatus(someProvider, someRefundReference, newRefundStatus);

        assertThat(refundTransactionEntity.getStatus(), is(newRefundStatus));
    }

    @Test
    public void canHandleNoRefundBeingFoundWithoutException() throws Exception {
        when(mockRefundTransactionDao.findByProviderAndReference(someProvider, someRefundReference))
                .thenReturn(Optional.empty());

        new RefundStatusUpdater(mockRefundTransactionDao)
                .updateRefundTransactionStatus(someProvider, someRefundReference, REFUND_SUBMITTED);
    }
}
