package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;

public class LinksConfig extends Configuration {
    private String cardDetailsUrl;

    public String getCardDetailsUrl() {
        return cardDetailsUrl;
    }
}
