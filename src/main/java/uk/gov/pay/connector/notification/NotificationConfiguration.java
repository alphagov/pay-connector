package uk.gov.pay.connector.notification;

import uk.gov.pay.connector.service.PaymentGatewayName;

public interface NotificationConfiguration {
    PaymentGatewayName getPaymentGatewayName();

    Boolean isNotificationEndpointSecured();

    String getNotificationDomain();
}
