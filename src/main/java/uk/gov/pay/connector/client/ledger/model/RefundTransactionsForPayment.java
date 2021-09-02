package uk.gov.pay.connector.client.ledger.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RefundTransactionsForPayment {

    String parentTransactionId;
    List<LedgerTransaction> transactions;

    public String getParentTransactionId() {
        return parentTransactionId;
    }

    public List<LedgerTransaction> getTransactions() {
        return transactions;
    }

    public void setParentTransactionId(String parentTransactionId) {
        this.parentTransactionId = parentTransactionId;
    }

    public void setTransactions(List<LedgerTransaction> transactions) {
        this.transactions = transactions;
    }
}
