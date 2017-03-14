package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public class GatewayCredentialsConfig extends Configuration {

    @Valid
    @NotNull
    private Map<String, String> urls;
    private List<String> credentials;
    private Map<String, Map<String, String>> jerseyClientOverrides;

    public List<String> getCredentials() {
        return credentials;
    }

    public Map<String, String> getUrls() {
        return urls;
    }

    public Map<String, Map<String, String>> getJerseyClientOverrides() {
        return jerseyClientOverrides;
    }
}
