package uk.gov.pay.connector.model;

import java.time.ZonedDateTime;

public interface Notification<T> {

    String getTransactionId();

    String getReference();

    T getStatus();

    ZonedDateTime getGatewayEventDate();

}
