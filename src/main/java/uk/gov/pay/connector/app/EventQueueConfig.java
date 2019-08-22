package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;

public class EventQueueConfig extends Configuration {
    private Boolean eventQueueEnabled;
    private int paymentStateTransitionPollerNumberOfThreads;

    public Boolean getEventQueueEnabled() {
        return eventQueueEnabled;
    }

    public int getPaymentStateTransitionPollerNumberOfThreads() {
        return paymentStateTransitionPollerNumberOfThreads;
    }
}
