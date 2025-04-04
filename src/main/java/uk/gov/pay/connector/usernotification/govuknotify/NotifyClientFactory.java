package uk.gov.pay.connector.usernotification.govuknotify;

import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.NotifyConfiguration;
import uk.gov.service.notify.NotificationClient;

import jakarta.inject.Inject;

public class NotifyClientFactory {

    private final NotifyConfiguration configuration;

    @Inject
    public NotifyClientFactory(ConnectorConfiguration configuration) {
        this.configuration = configuration.getNotifyConfiguration();
    }

    public NotificationClient getInstance() {
        return newInstance(configuration.getApiKey(), configuration.getNotificationBaseURL());
    }

    public NotificationClient getInstance(String notifyApiKey) {
        return newInstance(notifyApiKey, configuration.getNotificationBaseURL());
    }

    private NotificationClient newInstance(String apiKey, String notificationBaseURL) {
        return new NotificationClient(apiKey, notificationBaseURL, null);
    }
}
