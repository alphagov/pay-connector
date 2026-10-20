package uk.gov.pay.connector.gateway.adyen.api;

import com.adyen.Client;
import com.adyen.httpclient.AdyenHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Optional;

import static com.adyen.enums.Environment.TEST;
import static org.apache.commons.lang3.StringUtils.isBlank;

public class AdyenHttpClientFactory {
    public static final String PROXY_HOST_PROPERTY = "https.proxyHost";
    public static final String PROXY_PORT_PROPERTY = "https.proxyPort";

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenHttpClientFactory.class);

    private AdyenHttpClientFactory() {
    }

    private static Optional<AdyenHttpClient> getAdyenHttpClientWithProxy() {
        if (System.getProperty(PROXY_HOST_PROPERTY) != null && System.getProperty(PROXY_PORT_PROPERTY) != null) {
            try {
                int validPort = Integer.parseInt(System.getProperty(PROXY_PORT_PROPERTY));
                String host = System.getProperty(PROXY_HOST_PROPERTY);

                if (isBlank(host)) {
                    LOGGER.atWarn()
                            .setMessage("Proxy host is not configured. Creating AdyenHttpClient without proxy configuration")
                            .log();
                    return Optional.empty();
                }

                Proxy proxy = new Proxy(Proxy.Type.HTTP,
                        new InetSocketAddress(host, validPort));

                AdyenHttpClient adyenHttpClientWithProxy = new AdyenHttpClient();
                adyenHttpClientWithProxy.setProxy(proxy);

                return Optional.of(adyenHttpClientWithProxy);
            } catch (NumberFormatException _) {
                LOGGER.atWarn()
                        .setMessage("Invalid proxy port configured. Creating AdyenHttpClient without proxy configuration")
                        .log();
            }
        }

        return Optional.empty();
    }

    public static Client createAdyenHttpClientForTestEnvironment(String apiKey) {
        Client client = new Client(apiKey, TEST);
        getAdyenHttpClientWithProxy().ifPresent(client::setHttpClient);
        return client;
    }
}
