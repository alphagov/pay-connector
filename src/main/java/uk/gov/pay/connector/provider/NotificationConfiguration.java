package uk.gov.pay.connector.provider;

import uk.gov.pay.connector.service.PaymentGatewayName;

public interface NotificationConfiguration {
    PaymentGatewayName getPaymentGatewayName();

    Boolean isNotificationEndpointSecured();

    String getNotificationDomain();
}
