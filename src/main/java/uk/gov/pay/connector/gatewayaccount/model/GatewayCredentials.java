package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Optional;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public interface GatewayCredentials {

    @JsonIgnore
    default Optional<String> getGooglePayMerchantId() {
        return Optional.empty();
    }

    boolean hasCredentials();

    public class Views {
        public static class Api {
        }
    }
}
