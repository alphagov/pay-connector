package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;

public class TransactionsPaginationServiceConfig extends Configuration {

    private int displayPageSize;

    public int getDisplayPageSize() {
        return displayPageSize;
    }
}
