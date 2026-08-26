package uk.gov.pay.connector.gateway;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.httpclient5.InstrumentedHttpClientConnectionManager;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.util.Duration;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.impl.io.DefaultHttpClientConnectionOperator;
import org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.io.HttpClientConnectionOperator;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.glassfish.jersey.apache5.connector.Apache5ConnectorProvider;
import org.glassfish.jersey.client.ClientProperties;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.OperationOverrides;
import uk.gov.service.payments.logging.RestClientLoggingFilter;

import javax.net.ssl.SSLContext;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import static java.lang.String.format;
import static org.glassfish.jersey.apache5.connector.Apache5ClientProperties.CONNECTION_MANAGER;
import static org.glassfish.jersey.apache5.connector.Apache5ClientProperties.DISABLE_COOKIES;
import static org.glassfish.jersey.client.ClientProperties.READ_TIMEOUT;

public class ClientFactory {
    private final Environment environment;
    private final ConnectorConfiguration conf;

    private static final String PROXY_HOST_PROPERTY = "https.proxyHost";
    private static final String PROXY_PORT_PROPERTY = "https.proxyPort";

    @Inject
    public ClientFactory(Environment environment, ConnectorConfiguration conf) {
        this.environment = environment;
        this.conf = conf;
    }

    public Client createWithDropwizardClient(PaymentGatewayName gateway, GatewayOperation operation, MetricRegistry metricRegistry) {
        return createWithDropwizardClient(gateway, getReadTimeout(operation, gateway), operation.getConfigKey(), metricRegistry);
    }

    public Client createWithDropwizardClient(PaymentGatewayName gateway, MetricRegistry metricRegistry) {
        return createWithDropwizardClient(gateway, conf.getCustomJerseyClient().getReadTimeout(), "all", metricRegistry);
    }

    private Client createWithDropwizardClient(PaymentGatewayName gateway, Duration readTimeout, String metricName, MetricRegistry metricRegistry) {
        JerseyClientBuilder defaultClientBuilder = new JerseyClientBuilder(environment)
                .using(new Apache5ConnectorProvider())
                .using(conf.getClientConfiguration())
                .withProperty(READ_TIMEOUT, (int) readTimeout.toMilliseconds())
                .withProperty(DISABLE_COOKIES, true)
                .withProperty(CONNECTION_MANAGER,
                        createConnectionManager(gateway.getName(), metricName, metricRegistry, conf.getCustomJerseyClient().getConnectionTTL()));

        if (System.getProperty(PROXY_HOST_PROPERTY) != null && System.getProperty(PROXY_PORT_PROPERTY) != null) {
            defaultClientBuilder.withProperty(ClientProperties.PROXY_URI, format("http://%s:%s",
                    System.getProperty(PROXY_HOST_PROPERTY), System.getProperty(PROXY_PORT_PROPERTY))
            );
        }

        Client client = defaultClientBuilder.build(gateway.getName());
        client.register(RestClientLoggingFilter.class);

        return client;
    }

    private Duration getReadTimeout(GatewayOperation operation, PaymentGatewayName gateway) {
        return getOverridesFor(operation, gateway)
                .map(OperationOverrides::getReadTimeout)
                .orElse(conf.getCustomJerseyClient().getReadTimeout());
    }

    private Optional<OperationOverrides> getOverridesFor(GatewayOperation operation, PaymentGatewayName gateway) {
        if (gateway.equals(PaymentGatewayName.STRIPE)) return Optional.empty();
        return conf.getGatewayConfigFor(gateway)
                .getJerseyClientOverrides()
                .map(jerseyClientOverrides -> jerseyClientOverrides.getOverridesFor(operation));
    }

    private HttpClientConnectionManager createConnectionManager(String gatewayName, String operation,
                                                                MetricRegistry metricRegistry,
                                                                Duration connectionTimeToLive) {
        try {
            var TlsSocketStrategyLookup = RegistryBuilder.<TlsSocketStrategy>create()
                    .register(URIScheme.HTTPS.id, createTlsSocketStrategy(gatewayName, operation))
                    .build();

            HttpClientConnectionOperator httpClientConnectionOperator = new DefaultHttpClientConnectionOperator(
                    null,
                    SystemDefaultDnsResolver.INSTANCE,
                    TlsSocketStrategyLookup);

            return InstrumentedHttpClientConnectionManager.builder(metricRegistry)
                    .httpClientConnectionOperator(httpClientConnectionOperator)
                    .connFactory(new ManagedHttpClientConnectionFactory())
                    .timeToLive(TimeValue.ofMilliseconds(connectionTimeToLive.toMilliseconds()))
                    .name(format("%s.%s", gatewayName, operation)).build();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Unable to create TLS socket strategy", e);
        }
    }


    static TlsSocketStrategy createTlsSocketStrategy(String gatewayName, String operation) throws NoSuchAlgorithmException {
        return new TlsLoggingSocketStrategy(SSLContext.getDefault(), gatewayName, operation);
    }

}
