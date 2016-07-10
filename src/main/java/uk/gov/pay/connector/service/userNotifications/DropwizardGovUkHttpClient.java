package uk.gov.pay.connector.service.userNotifications;

import uk.gov.notifications.client.http.GovNotifyHttpClient;
import uk.gov.notifications.client.http.GovNotifyHttpClientRequest;
import uk.gov.notifications.client.http.GovNotifyHttpClientResponse;
import uk.gov.pay.connector.service.ClientFactory;

import javax.inject.Inject;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

public class DropwizardGovUkHttpClient implements GovNotifyHttpClient {

    private final ClientFactory httpClientFactory;

    @Inject
    public DropwizardGovUkHttpClient(ClientFactory httpClientFactory) {
        this.httpClientFactory = httpClientFactory;
    }

    @Override
    public GovNotifyHttpClientResponse send(GovNotifyHttpClientRequest govNotifyHttpClientRequest) throws Exception {
        MultivaluedMap headersMap = new MultivaluedHashMap<>();
        for (String key: govNotifyHttpClientRequest.getHeaders().keySet()) {
            headersMap.putSingle(key, govNotifyHttpClientRequest.getHeaders().get(key));
        }

        Response response = this.httpClientFactory
                .createWithDropwizardClient("USER_NOTIFICATION")
                .target(govNotifyHttpClientRequest.getUrl())
                .request(APPLICATION_JSON)
                .headers(headersMap)
                .post(Entity.json(govNotifyHttpClientRequest.getBody()));

        return GovNotifyHttpClientResponse.builder()
                .body(response.getEntity().toString())
                .statusCode(response.getStatus())
                .build();
    }
}
