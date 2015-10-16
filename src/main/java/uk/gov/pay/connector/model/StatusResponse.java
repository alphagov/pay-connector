package uk.gov.pay.connector.model;

public class StatusResponse {
    private String transactionId;
    private String status;

    public StatusResponse(String transactionId, String status) {
        this.transactionId = transactionId;
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public String getTransactionId() {
        return transactionId;
    }
}
