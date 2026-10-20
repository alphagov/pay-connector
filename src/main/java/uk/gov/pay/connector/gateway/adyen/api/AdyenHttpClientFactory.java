package uk.gov.pay.connector.gateway.adyen.api;

import com.adyen.Client;
import com.adyen.httpclient.AdyenHttpClient;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Optional;

import static com.adyen.enums.Environment.TEST;

public class AdyenHttpClientFactory {
    public static final String PROXY_HOST_PROPERTY = "https.proxyHost";
    public static final String PROXY_PORT_PROPERTY = "https.proxyPort";

    private AdyenHttpClientFactory() {
    }

    private static Optional<AdyenHttpClient> getAdyenHttpClientWithProxy() {
        if (System.getProperty(PROXY_HOST_PROPERTY) != null && System.getProperty(PROXY_PORT_PROPERTY) != null) {
            Proxy proxy = new Proxy(
                    Proxy.Type.HTTP,
                    new InetSocketAddress(System.getProperty(PROXY_HOST_PROPERTY),
                            Integer.parseInt(System.getProperty(PROXY_PORT_PROPERTY))));
            AdyenHttpClient adyenHttpClientWithProxy = new AdyenHttpClient();

            adyenHttpClientWithProxy.setProxy(proxy);

            return Optional.of(adyenHttpClientWithProxy);
        }

        return Optional.empty();
    }

    public static Client createAdyenHttpClientForTestEnvironment(String apiKey) {
        Client client = new Client(apiKey, TEST);
        getAdyenHttpClientWithProxy().ifPresent(client::setHttpClient);
        return client;
    }
}
