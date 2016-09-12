package uk.gov.pay.connector.app;

public class WorldpayNotificationConfig extends GatewayCredentialsConfig {
    private boolean secureNotificationEnabled;
    private String notificationDomain;

    public String getNotificationDomain() {
        return notificationDomain;
    }

    public boolean isSecureNotificationEnabled() {
        return secureNotificationEnabled;
    }
}
