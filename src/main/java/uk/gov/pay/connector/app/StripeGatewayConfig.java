package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;

import javax.validation.Valid;
import javax.validation.constraints.Min;
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

    @Valid
    @NotNull
    @Min(1)
    private int notification3dsWaitDelay;

    @Valid
    @NotNull
    private List<String> allowedCidrs;

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

    public int getNotification3dsWaitDelay() {
        return notification3dsWaitDelay;
    }

    public List<String> getAllowedCidrs() {
        return allowedCidrs;
    }

    public List<String> getCredentials() {
        return List.of("stripe_account_id");
    }
}
