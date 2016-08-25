package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.RefundStatus;

import java.util.Optional;

public class ExtendedNotification<T> implements Notification {

    private Notification<T> notification;
    private Optional<Enum> internalStatus = Optional.empty();

    private ExtendedNotification(Notification<T> notification, Optional<Enum> internalStatus) {
        this.notification = notification;
        this.internalStatus = internalStatus;
    }

    public static <T, R> ExtendedNotification<T> extend(Notification<T> notification, Optional<R> internalStatus) {
        return new ExtendedNotification(notification, internalStatus);
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
