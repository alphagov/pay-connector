package uk.gov.pay.connector.app;


import io.dropwizard.core.Configuration;

public class LinksConfig extends Configuration {

    private String frontendUrl;
    public String getFrontendUrl() {
        return frontendUrl;
    }
}
