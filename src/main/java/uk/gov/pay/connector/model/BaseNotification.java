package uk.gov.pay.connector.model;

import java.time.LocalDate;
import java.time.ZonedDateTime;

public class BaseNotification<T> implements Notification {

    private String reference;
    private String transactionId;
    private T status;
    private ZonedDateTime generationTime;

    public BaseNotification(String transactionId, String reference, T status, ZonedDateTime generationTime) {
        this.reference = reference;
        this.transactionId = transactionId;
        this.status = status;
        this.generationTime = generationTime;
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

    @Override
    public ZonedDateTime getGenerationTime() {
        return generationTime;
    }
}
