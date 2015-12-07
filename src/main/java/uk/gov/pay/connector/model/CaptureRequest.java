package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.ServiceAccount;

public class CaptureRequest {

    private String amount;
    private String transactionId;
    private ServiceAccount serviceAccount;


    public CaptureRequest(String amount, String transactionId, ServiceAccount serviceAccount) {
        this.amount = amount;
        this.transactionId = transactionId;
        this.serviceAccount = serviceAccount;
    }

    public static CaptureRequest captureRequest(String transactionId, String amount, ServiceAccount serviceAccount) {
        return new CaptureRequest(amount, transactionId, serviceAccount);
    }

    public String getAmount() {
        return amount;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public ServiceAccount getServiceAccount() {
        return serviceAccount;
    }
}
