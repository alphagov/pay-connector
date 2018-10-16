package uk.gov.pay.connector.model;


import org.apache.http.NameValuePair;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public class EvaluatedRefundStatusNotification<T> implements EvaluatedNotification<T> {

    private final Notification<T> notification;
    private final RefundStatus refundStatus;

    public EvaluatedRefundStatusNotification(Notification<T> notification, RefundStatus refundStatus) {
        this.notification = notification;
        this.refundStatus = refundStatus;
    }

    @Override
    public String getTransactionId() {
        return notification.getTransactionId();
    }

    @Override
    public String getReference() {
        return notification.getReference();
    }

    @Override
    public T getStatus() {
        return notification.getStatus();
    }

    @Override
    public ZonedDateTime getGatewayEventDate() {
        return notification.getGatewayEventDate();
    }

    @Override
    public Optional<List<NameValuePair>> getPayload() {
        return notification.getPayload();
    }

    @Override
    public boolean isOfRefundType() {
        return true;
    }

    public RefundStatus getRefundStatus() {
        return refundStatus;
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("EvaluatedChargeStatusNotification[")
                .append(notification)
                .append(" mapping to ")
                .append(refundStatus)
                .append("]")
                .toString();
    }

}
