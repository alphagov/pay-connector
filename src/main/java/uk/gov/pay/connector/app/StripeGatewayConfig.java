package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Optional;

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
    private Double feePercentage;

    @Valid
    @NotNull
    private Boolean collectFee;

    public String getUrl() {
        return url;
    }

    public StripeAuthTokens getAuthTokens() {
        return authTokens;
    }

    public StripeWebhookSigningSecrets getWebhookSigningSecrets() {
        return webhookSigningSecrets;
    }


    public Double getFeePercentage() {
        return Optional.ofNullable(feePercentage).orElse(0.0);
    }


    public Boolean isCollectFee() {
        return collectFee;
    }
}
