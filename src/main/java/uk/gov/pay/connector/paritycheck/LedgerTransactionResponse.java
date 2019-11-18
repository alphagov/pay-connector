package uk.gov.pay.connector.paritycheck;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class LedgerTransactionResponse {

    private String parentTransactionId;
    private List<LedgerTransaction> transactions;

    public List<LedgerTransaction> getTransactions() {
        return transactions;
    }

    public String getParentTransactionId() {
        return parentTransactionId;
    }
}
