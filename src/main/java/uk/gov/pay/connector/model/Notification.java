package uk.gov.pay.connector.model;

public interface Notification<T> {

    String getTransactionId();

    String getReference();

    T getStatus();

}
