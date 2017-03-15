package uk.gov.pay.connector.app;

public class WorldpayConfig extends GatewayConfig {
    private boolean secureNotificationEnabled;
    private String notificationDomain;

    public String getNotificationDomain() {
        return notificationDomain;
    }

    public boolean isSecureNotificationEnabled() {
        return secureNotificationEnabled;
    }
}
