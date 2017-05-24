package uk.gov.pay.connector.model;

import java.time.ZonedDateTime;
import java.util.Optional;

public class BaseNotification<T> implements Notification {

    private String reference;
    private String transactionId;
    private T status;
    private ZonedDateTime gatewayEventDate;
    private Object payload;

    public BaseNotification(String transactionId, String reference, T status, ZonedDateTime gatewayEventDate, Object payload) {
        this.reference = reference;
        this.transactionId = transactionId;
        this.status = status;
        this.gatewayEventDate = gatewayEventDate;
        this.payload = payload;
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
    public ZonedDateTime getGatewayEventDate() {
        return gatewayEventDate;
    }

    @Override
    public Optional<?> getPayload() {
        return Optional.ofNullable(payload);
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("Notification [")
                .append("reference=")
                .append(reference)
                .append(", transactionId=")
                .append(transactionId)
                .append(", status=")
                .append(status)
                .append(", gatewayEventDate=")
                .append(gatewayEventDate)
                .append("]")
                .toString();
    }
}
