package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.GatewayAccount;

public class CancelRequest {

    private String transactionId;
    private GatewayAccount gatewayAccount;

    private CancelRequest(String transactionId, GatewayAccount gatewayAccount) {
        this.transactionId = transactionId;
        this.gatewayAccount = gatewayAccount;
    }

    public static CancelRequest cancelRequest(String transactionId, GatewayAccount gatewayAccount) {
        return new CancelRequest(transactionId, gatewayAccount);
    }

    public String getTransactionId() {
        return transactionId;
    }

    public GatewayAccount getGatewayAccount() {
        return gatewayAccount;
    }
}
