package uk.gov.pay.connector.app;

public class SmartpayCredentialsConfig extends GatewayCredentialsConfig {
    private NotificationCredentials notification;


    public NotificationCredentials getNotification() {
        return notification;
    }
}
