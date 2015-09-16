package uk.gov.pay.connector.model;

public class CaptureRequest {

    private Amount amount;

    private String transactionId;


    public CaptureRequest(Amount amount, String transactionId) {
        this.amount = amount;
        this.transactionId = transactionId;
    }

    public Amount getAmount() {
        return amount;
    }

    public String getTransactionId() {
        return transactionId;
    }

}
