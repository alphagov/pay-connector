package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public interface GatewayCredentials {
    
    // Only applies to Worldpay, but we include it as a root field in some API responses returning the gateway account.
    // Having this as an interface method makes this easier to retrieve for this purpose.
    @JsonIgnore
    default String getGooglePayMerchantId() {
        return null;
    }
    
    boolean hasCredentials();
}
