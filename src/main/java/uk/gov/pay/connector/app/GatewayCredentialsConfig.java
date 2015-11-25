package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

public class GatewayCredentialsConfig extends Configuration {

    @Valid
    @NotNull
    private String url;

    private String username;

    private String password;

    private List<String> credentials;

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public List<String> getCredentials() {
        return credentials;
    }
}
