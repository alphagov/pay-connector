package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

public class GatewayCredentialsConfig extends Configuration {

    @Valid
    @NotNull
    private String url;

    private List<String> credentials;

    public String getUrl() {
        return url;
    }

    public List<String> getCredentials() {
        return credentials;
    }
}
