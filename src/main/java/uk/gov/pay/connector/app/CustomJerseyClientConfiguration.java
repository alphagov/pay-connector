package uk.gov.pay.connector.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import io.dropwizard.util.Duration;

import javax.validation.constraints.NotNull;

public class CustomJerseyClientConfiguration extends Configuration {
    private Duration readTimeout = Duration.milliseconds(20000L);

    @JsonProperty
    public Duration getReadTimeout() {
        return this.readTimeout;
    }
}
