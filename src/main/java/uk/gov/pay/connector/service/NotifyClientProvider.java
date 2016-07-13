package uk.gov.pay.connector.service;

import com.google.inject.Inject;
import com.google.inject.Provider;
import uk.gov.notifications.client.api.ClientConfiguration;
import uk.gov.notifications.client.api.GovNotifyApiClient;
import uk.gov.notifications.client.http.ApacheGovNotifyHttpClient;
import uk.gov.pay.connector.app.ConnectorConfiguration;

public class NotifyClientProvider implements Provider<GovNotifyApiClient> {

    @Inject
    private ConnectorConfiguration configuration;

    @Override
    public GovNotifyApiClient get() {
        ClientConfiguration config = new ClientConfiguration.Builder()
                .serviceId(configuration.getNotifyConfiguration().getServiceId())
                .baseUrl(configuration.getNotifyConfiguration().getNotificationBaseURL())
                .secret(configuration.getNotifyConfiguration().getSecret())
                .build();

        ApacheGovNotifyHttpClient client = new ApacheGovNotifyHttpClient();
        return new GovNotifyApiClient(config, client);
    }

}
