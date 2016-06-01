package uk.gov.pay.connector.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import io.dropwizard.util.Duration;

import javax.validation.Valid;

public class CustomJerseyClientConfiguration extends Configuration {
    private Duration readTimeout;

    @Valid
    @JsonProperty
    private String enableProxy;

    @JsonProperty
    public Duration getReadTimeout() {
        return this.readTimeout;
    }

    public boolean isProxyEnabled() {
        return "true".equals(enableProxy);
    }
}
