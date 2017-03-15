package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public class GatewayConfig extends Configuration {

    @Valid
    @NotNull
    private Map<String, String> urls;
    private List<String> credentials;

    private JerseyClientOverrides jerseyClientOverrides;

    public JerseyClientOverrides getJerseyClientOverrides() {
        return jerseyClientOverrides;
    }

    public List<String> getCredentials() {
        return credentials;
    }

    public Map<String, String> getUrls() {
        return urls;
    }
}
