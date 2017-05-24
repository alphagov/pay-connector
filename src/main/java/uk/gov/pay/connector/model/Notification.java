package uk.gov.pay.connector.model;

import java.time.ZonedDateTime;
import java.util.Optional;

public interface Notification<T> {

    String getTransactionId();

    String getReference();

    T getStatus();

    ZonedDateTime getGatewayEventDate();

    Optional<?> getPayload();

}
