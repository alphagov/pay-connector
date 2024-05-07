package uk.gov.pay.connector.app;

import io.dropwizard.core.Configuration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GatewayConfig extends Configuration {

    @Valid
    @NotNull
    private Map<String, String> urls;
    private List<String> credentials;

    private JerseyClientOverrides jerseyClientOverrides;

    private List<String> allowedCidrs;

    public Optional<JerseyClientOverrides> getJerseyClientOverrides() {
        return Optional.ofNullable(jerseyClientOverrides);
    }

    public List<String> getCredentials() {
        return credentials;
    }

    public Map<String, String> getUrls() {
        return urls;
    }

    public List<String> getAllowedCidrs() {
        return allowedCidrs;
    }
}
