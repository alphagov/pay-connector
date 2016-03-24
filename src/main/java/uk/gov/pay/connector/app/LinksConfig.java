package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;

import java.net.URI;

public class LinksConfig extends Configuration {

    private URI cardDetailsBaseUrl;

    public URI getCardDetailsBaseUrl() {
        return cardDetailsBaseUrl;
    }
}
