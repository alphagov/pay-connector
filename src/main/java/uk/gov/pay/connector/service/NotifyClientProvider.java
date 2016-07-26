package uk.gov.pay.connector.service;

import com.google.inject.Inject;
import com.google.inject.Provider;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.service.notify.NotificationClient;

public class NotifyClientProvider implements Provider<NotificationClient> {

    private ConnectorConfiguration configuration;

    @Inject
    public NotifyClientProvider(ConnectorConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public NotificationClient get() {

        return new NotificationClient(configuration.getNotifyConfiguration().getSecret(),
                configuration.getNotifyConfiguration().getServiceId(),
                configuration.getNotifyConfiguration().getNotificationBaseURL()
        );
    }

}