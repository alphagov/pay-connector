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
    private StripeAuthTokens authTokens;

    @Valid
    @NotNull
    private StripeWebhookSigningSecrets webhookSigningSecrets;

    @Valid
    private String platformAccountId;

    public String getUrl() {
        return url;
    }

    public StripeAuthTokens getAuthTokens() {
        return authTokens;
    }

    public StripeWebhookSigningSecrets getWebhookSigningSecrets() {
        return webhookSigningSecrets;
    }

    public String getPlatformAccountId() {
        return platformAccountId;
    }
}
