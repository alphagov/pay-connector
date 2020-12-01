package uk.gov.pay.connector.app;


import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Map;

public class WorldpayConfig extends GatewayConfig {
    
    private boolean secureNotificationEnabled;
    
    private String notificationDomain;
    
    private ApplePayConfig applePayConfig;
    
    @Valid
    @NotNull
    private Map<String, String> threeDsFlexDdcUrls;

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

    public Map<String, String> getThreeDsFlexDdcUrls() {
        return threeDsFlexDdcUrls;
    }
}
