package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.GatewayAccount;

public class CaptureRequest {

    private String amount;
    private String transactionId;
    private GatewayAccount gatewayAccount;


    public CaptureRequest(String amount, String transactionId, GatewayAccount gatewayAccount) {
        this.amount = amount;
        this.transactionId = transactionId;
        this.gatewayAccount = gatewayAccount;
    }

    public static CaptureRequest captureRequest(String transactionId, String amount, GatewayAccount gatewayAccount) {
        return new CaptureRequest(amount, transactionId, gatewayAccount);
    }

    public String getAmount() {
        return amount;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public GatewayAccount getGatewayAccount() {
        return gatewayAccount;
    }
}
