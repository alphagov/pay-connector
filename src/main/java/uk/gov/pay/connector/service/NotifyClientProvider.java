package uk.gov.pay.connector.service;

import com.google.inject.Inject;
import com.google.inject.Provider;
import org.apache.commons.lang3.StringUtils;
import uk.gov.notifications.client.api.ClientConfiguration;
import uk.gov.notifications.client.api.GovNotifyApiClient;
import uk.gov.notifications.client.http.ApacheGovNotifyHttpClient;
import uk.gov.pay.connector.app.ConnectorConfiguration;

public class NotifyClientProvider implements Provider<GovNotifyApiClient> {

    @Inject
    private ConnectorConfiguration configuration;

    @Inject
    private ClientFactory clientFactory;

    @Override
    public GovNotifyApiClient get() {
        ClientConfiguration config = new ClientConfiguration.Builder()
                .serviceId(readServiceIDConfig())
                .baseUrl(readBaseURLConfig())
                .secret(readSecretConfig())
                .build();

        ApacheGovNotifyHttpClient client = new ApacheGovNotifyHttpClient();
        return new GovNotifyApiClient(config, client);
    }

    private String readServiceIDConfig() {
        if (StringUtils.isBlank(configuration.getNotifyConfiguration().getServiceId())) {
            throw new RuntimeException("config property 'serviceID' for notify is missing or not set");
        }
        return configuration.getNotifyConfiguration().getServiceId();
    }

    private String readBaseURLConfig() {
        if (StringUtils.isBlank(configuration.getNotifyConfiguration().getNotificationBaseURL())) {
            throw new RuntimeException("config property 'notificationBaseURL' for notify is missing or not set");
        }
        return configuration.getNotifyConfiguration().getNotificationBaseURL();
    }

    private String readSecretConfig() {
        if (StringUtils.isBlank(configuration.getNotifyConfiguration().getSecret())) {
            throw new RuntimeException("config property 'secret' is missing or not set for notify");
        }
        return configuration.getNotifyConfiguration().getSecret();
    }

}
