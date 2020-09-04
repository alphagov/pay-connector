package uk.gov.pay.connector.client.ledger.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
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
}
