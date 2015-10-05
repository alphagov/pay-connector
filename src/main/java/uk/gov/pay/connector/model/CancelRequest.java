package uk.gov.pay.connector.model;

public class CancelRequest {

    private String transactionId;

    private CancelRequest(String transactionId) {
        this.transactionId = transactionId;
    }

    public static CancelRequest cancelRequest(String transactionId) {
        return new CancelRequest(transactionId);
    }

    public String getTransactionId() {
        return transactionId;
    }
}
