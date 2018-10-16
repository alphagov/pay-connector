package uk.gov.pay.connector.usernotification.model;

import java.time.ZonedDateTime;

public interface EvaluatedNotification<T> extends Notification<T> {

    String getTransactionId();

    T getStatus();

    ZonedDateTime getGatewayEventDate();

    default boolean isOfChargeType() {
        return false;
    }

    default boolean isOfRefundType() {
        return false;
    }

}
