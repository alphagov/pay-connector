package uk.gov.pay.connector.app.adyen;

import io.dropwizard.core.Configuration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import uk.gov.pay.connector.app.JerseyClientOverrides;

import java.util.Optional;

public class AdyenGatewayConfig extends Configuration {

    @Valid
    @NotNull
    private BaseUrls baseUrls;

    @Valid
    @NotNull
    private AdyenIds merchantAccountIds;

    @Valid
    @NotNull
    private AdyenIds balancePlatformIds;

    @Valid
    @NotNull
    private ApiKeys apiKeys;

    @NotBlank
    private String notificationDomain;

    @Valid
    @NotNull
    private HmacKeys hmacKeys;

    private JerseyClientOverrides jerseyClientOverrides;

    public BaseUrls getBaseUrls() {
        return baseUrls;
    }

    public AdyenIds getMerchantAccountIds() {
        return merchantAccountIds;
    }

    public AdyenIds getBalancePlatformIds() {
        return balancePlatformIds;
    }

    public ApiKeys getApiKeys() {
        return apiKeys;
    }

    public String getNotificationDomain() {
        return notificationDomain;
    }

    public HmacKeys getHmacKeys() {
        return hmacKeys;
    }

    public Optional<JerseyClientOverrides> getJerseyClientOverrides() {
        return Optional.ofNullable(jerseyClientOverrides);
    }
}

