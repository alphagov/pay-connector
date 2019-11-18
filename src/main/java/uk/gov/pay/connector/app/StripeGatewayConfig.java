package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class StripeGatewayConfig extends Configuration {

    @Valid
    @NotNull
    private String url;

    @Valid
    @NotNull
    private StripeAuthTokens authTokens;

    @Valid
    @NotNull
    private List<String> webhookSigningSecrets;
    
    @Valid
    private Double feePercentage;

    @Valid
    private String platformAccountId;

    @Valid
    @NotNull
    private Boolean collectFee;

    public String getUrl() {
        return url;
    }

    public StripeAuthTokens getAuthTokens() {
        return authTokens;
    }

    public List<String> getWebhookSigningSecrets() {
        return webhookSigningSecrets.stream().filter(s -> !s.isBlank()).collect(Collectors.toList());
    }
    
    public Double getFeePercentage() {
        return Optional.ofNullable(feePercentage).orElse(0.0);
    }

    public Boolean isCollectFee() {
        return collectFee;
    }

    public String getPlatformAccountId() {
        return platformAccountId;
    }
}
