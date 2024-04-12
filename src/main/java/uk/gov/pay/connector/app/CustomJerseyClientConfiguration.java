package uk.gov.pay.connector.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.core.Configuration;
import io.dropwizard.util.Duration;

public class CustomJerseyClientConfiguration extends Configuration {
    
    private Duration readTimeout;
    
    private Duration connectionTTL;

    @JsonProperty
    public Duration getReadTimeout() {
        return this.readTimeout;
    }

    @JsonProperty
    public Duration getConnectionTTL() {
        return connectionTTL;
    }
}
