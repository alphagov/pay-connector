package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;

public class TransactionsPaginationServiceConfig extends Configuration {

    private long displayPageSize;

    public long getDisplayPageSize() {
        return displayPageSize;
    }
}
