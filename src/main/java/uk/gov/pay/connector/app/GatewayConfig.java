package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GatewayConfig extends Configuration {
    public GatewayConfig() {}

    public GatewayConfig(Map<String, String> urls, List<String> credentials, JerseyClientOverrides jerseyClientOverrides) {
        this.urls = urls;
        this.credentials = credentials;
        this.jerseyClientOverrides = jerseyClientOverrides;
    }

    @Valid
    @NotNull
    private Map<String, String> urls;

    /**
     * Credential names stored in the GatewayAccount::credentials map
     */
    private List<String> credentials;

    private JerseyClientOverrides jerseyClientOverrides;

    public Optional<JerseyClientOverrides> getJerseyClientOverrides() {
        return Optional.ofNullable(jerseyClientOverrides);
    }

    public List<String> getCredentials() {
        return credentials;
    }

    public Map<String, String> getUrls() {
        return urls;
    }
}
