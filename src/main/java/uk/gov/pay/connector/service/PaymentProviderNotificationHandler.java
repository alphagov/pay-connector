package uk.gov.pay.connector.service;

import fj.data.Either;
import uk.gov.pay.connector.model.Notification;
import uk.gov.pay.connector.model.Notifications;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

public interface PaymentProviderNotificationHandler<R> extends PaymentProvider {
    StatusMapper getStatusMapper();

    Either<String, Notifications<R>> parseNotification(String payload);

    Boolean isNotificationEndpointSecured();

    String getNotificationDomain();

    boolean verifyNotification(Notification<R> notification, GatewayAccountEntity gatewayAccountEntity);
}
