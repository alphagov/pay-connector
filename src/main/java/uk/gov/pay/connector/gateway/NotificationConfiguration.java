package uk.gov.pay.connector.gateway;

public interface NotificationConfiguration {
    PaymentGatewayName getPaymentGatewayName();

    Boolean isNotificationEndpointSecured();

    String getNotificationDomain();
}
