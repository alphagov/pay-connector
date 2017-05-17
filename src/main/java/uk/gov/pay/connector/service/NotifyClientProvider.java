package uk.gov.pay.connector.service;

import com.google.inject.Inject;
import com.google.inject.Provider;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.NotifyConfiguration;
import uk.gov.service.notify.NotificationClient;

import javax.net.ssl.SSLContext;

import static uk.gov.pay.connector.util.TrustStoreLoader.getSSLContext;

public class NotifyClientProvider implements Provider<NotificationClient> {

    private NotifyConfiguration configuration;
    private final SSLContext sslContext;

    @Inject
    public NotifyClientProvider(ConnectorConfiguration configuration) {
        this.configuration = configuration.getNotifyConfiguration();
        this.sslContext = getSSLContext();
    }

    @Override
    public NotificationClient get() {
        return new NotificationClient(configuration.getApiKey(), configuration.getNotificationBaseURL(), null, sslContext);
    }

}
