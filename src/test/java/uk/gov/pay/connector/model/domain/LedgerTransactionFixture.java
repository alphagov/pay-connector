package uk.gov.pay.connector.model.domain;

import uk.gov.pay.connector.paritycheck.LedgerTransaction;
import uk.gov.pay.connector.paritycheck.TransactionState;

public class LedgerTransactionFixture {
    private String status = "created";

    public static LedgerTransactionFixture aValidLedgerTransaction() {
        return new LedgerTransactionFixture();
    }

    public LedgerTransaction build() {
        var ledgerTransaction = new LedgerTransaction();
        ledgerTransaction.setState(new TransactionState(status));
        return ledgerTransaction;
    }

    public LedgerTransactionFixture withStatus(String status) {
        this.status = status;
        return this;
    }
}
