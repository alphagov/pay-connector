package uk.gov.pay.connector.app;


public class WorldpayConfig extends GatewayConfig {
    private boolean secureNotificationEnabled;
    private String notificationDomain;
    private ApplePayConfig applePay;

    public String getNotificationDomain() {
        return notificationDomain;
    }

    public boolean isSecureNotificationEnabled() {
        return secureNotificationEnabled;
    }

    public ApplePayConfig getApplePay() {
        return applePay;
    }
}
