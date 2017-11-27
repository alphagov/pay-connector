package uk.gov.pay.connector.model.domain;

import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.model.domain.transaction.ChargeTransactionEntity;
import uk.gov.pay.connector.model.domain.transaction.RefundTransactionEntity;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class PaymentRequestEntityTest {

    private ChargeTransactionEntity expectedChargeTransaction;
    private PaymentRequestEntity paymentRequestEntity;

    @Before
    public void setUp() throws Exception {
        expectedChargeTransaction = new ChargeTransactionEntity();

        paymentRequestEntity = new PaymentRequestEntity();
        paymentRequestEntity.addTransaction(expectedChargeTransaction);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowExceptionWhenNoChargeTransactionsWhichIsAnInvalidState() throws Exception {
        new PaymentRequestEntity().getChargeTransaction();
    }

    @Test
    public void shouldReturnChargeTransactionWhenThereIsOnlyAChargeTransaction() throws Exception {
        ChargeTransactionEntity chargeTransaction = paymentRequestEntity.getChargeTransaction();
        assertThat(chargeTransaction, is(expectedChargeTransaction));
    }

    @Test
    public void shouldReturnChargeTransactionWhenThereAreChargeAndRefundTransactions() throws Exception {
        RefundTransactionEntity refundTransaction = new RefundTransactionEntity();
        paymentRequestEntity.addTransaction(refundTransaction);

        ChargeTransactionEntity chargeTransaction = paymentRequestEntity.getChargeTransaction();
        assertThat(chargeTransaction, is(expectedChargeTransaction));
    }

    @Test
    public void shouldNotReturnRefundTransactionWhenThereIsOnlyAChargeTransaction() throws Exception {
        List<RefundTransactionEntity> refundTransactions = paymentRequestEntity.getRefundTransactions();
        assertThat(refundTransactions.isEmpty(), is(true));
    }

    @Test
    public void shouldReturnRefundTransactionWhenThereAreChargeAndRefundTransactions() throws Exception {
        RefundTransactionEntity refundTransaction = new RefundTransactionEntity();
        String refundExternalId = "someRefundExternalId";
        refundTransaction.setRefundExternalId(refundExternalId);
        paymentRequestEntity.addTransaction(refundTransaction);

        List<RefundTransactionEntity> refundTransactions = paymentRequestEntity.getRefundTransactions();
        assertThat(refundTransactions.size(), is(1));
        assertThat(refundTransactions.get(0).getRefundExternalId(), is(refundExternalId));

    }

    @Test
    public void shouldReturnAllRefundTransactionWhenThereAreMutlipleRefundTransactions() throws Exception {
        RefundTransactionEntity refundTransaction = new RefundTransactionEntity();
        String refundReference = "someRefundReference";
        refundTransaction.setRefundExternalId(refundReference);
        paymentRequestEntity.addTransaction(refundTransaction);
        RefundTransactionEntity anotherRefundTransaction = new RefundTransactionEntity();
        String anotherRefundReference = "anotherRefundReference";
        anotherRefundTransaction.setRefundExternalId(anotherRefundReference);
        paymentRequestEntity.addTransaction(anotherRefundTransaction);

        List<RefundTransactionEntity> refundTransactions = paymentRequestEntity.getRefundTransactions();
        assertThat(refundTransactions.size(), is(2));
        assertThat(refundTransactions.get(0).getRefundExternalId(), is(refundReference));
        assertThat(refundTransactions.get(1).getRefundExternalId(), is(anotherRefundReference));
    }
}
