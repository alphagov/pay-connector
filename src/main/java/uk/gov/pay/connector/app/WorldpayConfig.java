package uk.gov.pay.connector.app;


import com.fasterxml.jackson.annotation.JsonProperty;

public class WorldpayConfig extends GatewayConfig {
    private boolean secureNotificationEnabled;
    private String notificationDomain;
    private ApplePayConfig applePayConfig;

    public String getNotificationDomain() {
        return notificationDomain;
    }

    public boolean isSecureNotificationEnabled() {
        return secureNotificationEnabled;
    }

    @JsonProperty("applePay")
    public ApplePayConfig getApplePayConfig() {
        return applePayConfig;
    }
}
