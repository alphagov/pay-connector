package uk.gov.pay.connector.model.domain;

import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.model.domain.transaction.TransactionEntity;
import uk.gov.pay.connector.model.domain.transaction.TransactionOperation;

import static org.hamcrest.core.Is.is;

import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.pay.connector.model.domain.transaction.TransactionOperation.CHARGE;

public class PaymentRequestEntityTest {

    private TransactionEntity expectedChargeTransaction;
    private PaymentRequestEntity paymentRequestEntity;

    @Before
    public void setUp() throws Exception {
        expectedChargeTransaction = new TransactionEntity();
        expectedChargeTransaction.setOperation(CHARGE);

        paymentRequestEntity = new PaymentRequestEntity();
        paymentRequestEntity.addTransaction(expectedChargeTransaction);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowExceptionWhenNoChargeTransactionsWhichIsAnInvalidState() throws Exception {
        new PaymentRequestEntity().getChargeTransaction();
    }

    @Test
    public void shouldReturnChargeTransactionWhenThereIsOnlyAChargeTransaction() throws Exception {
        TransactionEntity chargeTransaction = paymentRequestEntity.getChargeTransaction();
        assertThat(chargeTransaction, is(expectedChargeTransaction));
    }

    @Test
    public void shouldReturnChargeTransactionWhenThereIsManyTransactions() throws Exception {
        TransactionEntity refundTransaction = new TransactionEntity();
        refundTransaction.setOperation(TransactionOperation.REFUND);
        paymentRequestEntity.addTransaction(refundTransaction);

        TransactionEntity chargeTransaction = paymentRequestEntity.getChargeTransaction();
        assertThat(chargeTransaction, is(expectedChargeTransaction));
    }
}