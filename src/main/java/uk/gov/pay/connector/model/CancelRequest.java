package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.ServiceAccount;

public class CancelRequest {

    private String transactionId;
    private ServiceAccount serviceAccount;

    private CancelRequest(String transactionId, ServiceAccount serviceAccount) {
        this.transactionId = transactionId;
        this.serviceAccount = serviceAccount;
    }

    public static CancelRequest cancelRequest(String transactionId, ServiceAccount serviceAccount) {
        return new CancelRequest(transactionId, serviceAccount);
    }

    public String getTransactionId() {
        return transactionId;
    }

    public ServiceAccount getServiceAccount() {
        return serviceAccount;
    }
}
