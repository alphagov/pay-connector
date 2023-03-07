package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;

import java.time.Instant;

public class LinksConfig extends Configuration {

    private String frontendUrl;
    private String cardFrontendUrl;
    private boolean cardFrontendEnabled;

    public String getFrontendUrl() {
        if (getCardFrontendEnabled()) {
            return cardFrontendUrl;
        } else {
            return frontendUrl;
        }
    }

    public String getCardFrontendUrl() {
        return cardFrontendUrl;
    }

    public boolean getCardFrontendEnabled() {
        return cardFrontendEnabled;
    }
}
