package uk.gov.pay.connector.model;

public class CaptureRequest {

    private String amount;
    private String transactionId;


    public CaptureRequest(String amount, String transactionId) {
        this.amount = amount;
        this.transactionId = transactionId;
    }

    public static CaptureRequest captureRequest(String chargeId, String amount) {
        return new CaptureRequest(amount, chargeId);
    }

    public String getAmount() {
        return amount;
    }

    public String getTransactionId() {
        return transactionId;
    }

}
