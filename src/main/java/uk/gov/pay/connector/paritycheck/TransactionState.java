package uk.gov.pay.connector.paritycheck;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionState {
    private String status;

    public TransactionState(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}
