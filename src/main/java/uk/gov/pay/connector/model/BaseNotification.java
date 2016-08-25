package uk.gov.pay.connector.model;

public class BaseNotification<T> implements Notification {

    private String reference;
    private String transactionId;
    private T status;

    public BaseNotification(String reference, String transactionId, T status) {
        this.reference = reference;
        this.transactionId = transactionId;
        this.status = status;
    }

    @Override
    public String getTransactionId() {
        return this.transactionId;
    }

    @Override
    public String getReference() {
        return this.reference;
    }

    @Override
    public T getStatus() {
        return this.status;
    }
}
