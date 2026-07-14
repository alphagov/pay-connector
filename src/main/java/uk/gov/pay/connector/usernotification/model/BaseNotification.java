package uk.gov.pay.connector.usernotification.model;

import org.apache.http.NameValuePair;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public class BaseNotification<T> implements Notification<T> {

    private String reference;
    private String transactionId;
    private T status;
    private ZonedDateTime gatewayEventDate;
    private List<NameValuePair> payload;

    BaseNotification(String transactionId, String reference, T status, ZonedDateTime gatewayEventDate,
                     List<NameValuePair> payload) {
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
    public Optional<List<NameValuePair>> getPayload() {
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
