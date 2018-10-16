package uk.gov.pay.connector.usernotification.govuknotify;

import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.NotifyConfiguration;
import uk.gov.service.notify.NotificationClient;

import javax.inject.Inject;
import javax.net.ssl.SSLContext;

import static uk.gov.pay.connector.util.TrustStoreLoader.getSSLContext;

public class NotifyClientFactory {

    private final NotifyConfiguration configuration;
    private final SSLContext sslContext;

    @Inject
    public NotifyClientFactory(ConnectorConfiguration configuration) {
        this.configuration = configuration.getNotifyConfiguration();
        this.sslContext = getSSLContext();
    }

    public NotificationClient getInstance() {
        return newInstance(configuration.getApiKey(), configuration.getNotificationBaseURL());
    }

    public NotificationClient getInstance(String notifyApiKey) {
        return newInstance(notifyApiKey, configuration.getNotificationBaseURL());
    }

    private NotificationClient newInstance(String apiKey, String notificationBaseURL) {
        return new NotificationClient(apiKey, notificationBaseURL, null, sslContext);
    }
}
