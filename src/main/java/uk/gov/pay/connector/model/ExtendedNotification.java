package uk.gov.pay.connector.model;

import org.apache.http.NameValuePair;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.service.InterpretedStatus;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public class ExtendedNotification<T> implements Notification<T> {

    private Notification<T> notification;
    private InterpretedStatus interpretedStatus;

    private ExtendedNotification(Notification<T> notification, InterpretedStatus interpretedStatus) {
        this.notification = notification;
        this.interpretedStatus = interpretedStatus;
    }

    public static <T> ExtendedNotification<T> extend(Notification<T> notification, InterpretedStatus status) {
        return new ExtendedNotification<T>(notification, status);
    }

    public String getTransactionId() {
        return notification.getTransactionId();
    }

    @Override
    public String getReference() {
        return notification.getReference();
    }

    public T getStatus() {
        return notification.getStatus();
    }

    public ZonedDateTime getGatewayEventDate() { return notification.getGatewayEventDate(); }

    @Override
    public Optional<List<NameValuePair>> getPayload() {
        return notification.getPayload();
    }

    public InterpretedStatus getInterpretedStatus() {
        return interpretedStatus;
    }

    public boolean isOfChargeType() {
        return interpretedStatus.get().map(status -> status instanceof ChargeStatus).orElse(false);
    }

    public boolean isOfRefundType() {
        return interpretedStatus.get().map(status -> status instanceof RefundStatus).orElse(false);
    }
}
