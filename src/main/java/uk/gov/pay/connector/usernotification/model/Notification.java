package uk.gov.pay.connector.usernotification.model;

import org.apache.http.NameValuePair;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface Notification<T> {

    String getTransactionId();

    String getReference();

    T getStatus();

    ZonedDateTime getGatewayEventDate();

    Optional<List<NameValuePair>> getPayload();

}
