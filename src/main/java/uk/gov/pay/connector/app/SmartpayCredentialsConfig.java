package uk.gov.pay.connector.app;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SmartpayCredentialsConfig extends GatewayCredentialsConfig {
    private NotificationCredentials notification;


    public NotificationCredentials getNotification() {
        return notification;
    }
}
