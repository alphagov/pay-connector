package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class StripeGatewayConfig extends Configuration {

    @Valid
    @NotNull
    private String url;

    @Valid
    @NotNull
    private String authToken;

    public String getUrl() {
        return url;
    }

    public String getAuthToken() {
        return authToken;
    }
}
