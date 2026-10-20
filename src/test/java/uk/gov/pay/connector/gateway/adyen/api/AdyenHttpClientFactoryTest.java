package uk.gov.pay.connector.gateway.adyen.api;

import com.adyen.Client;
import com.adyen.httpclient.AdyenHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.adyen.enums.Environment.TEST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.pay.connector.gateway.adyen.api.AdyenHttpClientFactory.PROXY_HOST_PROPERTY;
import static uk.gov.pay.connector.gateway.adyen.api.AdyenHttpClientFactory.PROXY_PORT_PROPERTY;
import static uk.gov.pay.connector.gateway.adyen.api.AdyenHttpClientFactory.createAdyenHttpClientForTestEnvironment;

class AdyenHttpClientFactoryTest {

    @BeforeEach
    void setUp() {
        System.clearProperty(PROXY_HOST_PROPERTY);
        System.clearProperty(PROXY_PORT_PROPERTY);
    }

    @Test
    void shouldCreateClientWithApiKey() {
        Client client = createAdyenHttpClientForTestEnvironment("test-api-key");

        assertThat(client.getConfig().getApiKey(), is("test-api-key"));
        assertThat(client.getConfig().getEnvironment(), is(TEST));
    }

    @Test
    void shouldConfigureHttpClientWhenBothProxyPropertiesAreSet() {
        System.setProperty(PROXY_HOST_PROPERTY, "example.org");
        System.setProperty(PROXY_PORT_PROPERTY, "8080");

        Client client = createAdyenHttpClientForTestEnvironment("test-api-key");

        AdyenHttpClient adyenHttpClient = (AdyenHttpClient) client.getHttpClient();
        String proxyAddress = adyenHttpClient.getProxy().address().toString();

        assertThat(proxyAddress, containsString("example.org"));
        assertThat(proxyAddress, containsString("8080"));
    }

    @Test
    void shouldNotConfigureCustomProxyWhenHostIsMissing() {
        System.setProperty(PROXY_PORT_PROPERTY, "8080");

        Client client = createAdyenHttpClientForTestEnvironment("test-api-key");
        AdyenHttpClient adyenHttpClient = (AdyenHttpClient) client.getHttpClient();

        assertNull(adyenHttpClient.getProxy());
    }

    @Test
    void shouldNotConfigureCustomProxyWhenPortIsMissing() {
        System.setProperty(PROXY_HOST_PROPERTY, "localhost");

        Client client = createAdyenHttpClientForTestEnvironment("test-api-key");
        AdyenHttpClient adyenHttpClient = (AdyenHttpClient) client.getHttpClient();

        assertNull(adyenHttpClient.getProxy());
    }

    @Test
    void shouldThrowExceptionWhenProxyPortIsInvalid() {
        System.setProperty(PROXY_HOST_PROPERTY, "localhost");
        System.setProperty(PROXY_PORT_PROPERTY, "invalid");

        assertThrows(NumberFormatException.class,
                () -> createAdyenHttpClientForTestEnvironment("test-api-key"));
    }
}
