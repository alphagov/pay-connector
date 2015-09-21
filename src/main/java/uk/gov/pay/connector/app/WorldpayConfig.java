package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;

public class WorldpayConfig extends Configuration {
    private String url;
    private String username;
    private String password;

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
