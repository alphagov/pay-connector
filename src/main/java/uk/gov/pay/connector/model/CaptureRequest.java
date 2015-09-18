package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.Amount;

public class CaptureRequest {

    private Amount amount;
    private String transactionId;


    public CaptureRequest(Amount amount, String transactionId) {
        this.amount = amount;
        this.transactionId = transactionId;
    }

    public static CaptureRequest captureRequest(String chargeId, String amount) {
        return new CaptureRequest(new Amount(amount), chargeId);
    }

    public Amount getAmount() {
        return amount;
    }

    public String getTransactionId() {
        return transactionId;
    }

}
