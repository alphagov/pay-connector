package uk.gov.pay.connector.notification.worldpay;

import com.google.inject.Inject;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.WorldpayConfig;
import uk.gov.pay.connector.notification.NotificationConfiguration;
import uk.gov.pay.connector.service.PaymentGatewayName;

public class WorldpayNotificationConfiguration implements NotificationConfiguration {

    private final WorldpayConfig config;

    @Inject
    public WorldpayNotificationConfiguration(ConnectorConfiguration config) {
        this.config = config.getWorldpayConfig();
    }

    @Override
    public PaymentGatewayName getPaymentGatewayName() {
        return PaymentGatewayName.WORLDPAY;
    }

    @Override
    public Boolean isNotificationEndpointSecured() {
        return config.isSecureNotificationEnabled();
    }

    @Override
    public String getNotificationDomain() {
        return config.getNotificationDomain();
    }
}
