package uk.gov.pay.connector.model;

import org.apache.http.NameValuePair;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.RefundStatus;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public class ExtendedNotification<T> implements Notification<T> {

    private Notification<T> notification;
    private Optional<Enum> internalStatus = Optional.empty();

    private ExtendedNotification(Notification<T> notification, Optional<Enum> internalStatus) {
        this.notification = notification;
        this.internalStatus = internalStatus;
    }

    public static <T> ExtendedNotification<T> extend(Notification<T> notification, Optional<Enum> internalStatus) {
        return new ExtendedNotification<T>(notification, internalStatus);
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

    public Optional<Enum> getInternalStatus() {
        return internalStatus;
    }

    public boolean isOfChargeType() {
        if (!internalStatus.isPresent()) {
            return false;
        }
        return internalStatus.get() instanceof ChargeStatus;
    }

    public boolean isOfRefundType() {
        if (!internalStatus.isPresent()) {
            return false;
        }
        return internalStatus.get() instanceof RefundStatus;
    }
}
