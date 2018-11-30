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

    @Valid
    @NotNull
    private String webhookSigningSecret;

    public String getUrl() {
        return url;
    }

    public String getAuthToken() {
        return authToken;
    }

    public String getWebhookSigningSecret() {
        return webhookSigningSecret;
    }
}
