package uk.gov.pay.connector.model.domain;

import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.client.ledger.model.RefundTransactionsForPayment;

import java.util.List;

public class RefundTransactionsForPaymentFixture {
    private List<LedgerTransaction> transactions = List.of();
    private String parentTransactionId;
    
    public static RefundTransactionsForPaymentFixture aValidRefundTransactionsForPayment() {
        return new RefundTransactionsForPaymentFixture();
    }
    
    public RefundTransactionsForPaymentFixture withParentTransactionId(String parentTransactionId) {
        this.parentTransactionId = parentTransactionId;
        return this;
    }
    
    public RefundTransactionsForPaymentFixture withTransactions(List<LedgerTransaction> transactions) {
        this.transactions = transactions;
        return this;
    }
    
    public RefundTransactionsForPayment build() {
        RefundTransactionsForPayment refundTransactionsForPayment = new RefundTransactionsForPayment();
        refundTransactionsForPayment.setParentTransactionId(parentTransactionId);
        refundTransactionsForPayment.setTransactions(transactions);
        return refundTransactionsForPayment;
    }
}
