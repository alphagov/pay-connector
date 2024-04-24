package uk.gov.pay.connector.gateway;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.httpclient.InstrumentedHttpClientConnectionManager;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.setup.Environment;
import io.dropwizard.util.Duration;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.conn.ManagedHttpClientConnectionFactory;
import org.apache.http.impl.conn.SystemDefaultDnsResolver;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientProperties;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.OperationOverrides;
import uk.gov.service.payments.logging.RestClientLoggingFilter;

import javax.inject.Inject;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.ws.rs.client.Client;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static org.glassfish.jersey.apache.connector.ApacheClientProperties.CONNECTION_MANAGER;
import static org.glassfish.jersey.apache.connector.ApacheClientProperties.DISABLE_COOKIES;
import static org.glassfish.jersey.client.ClientProperties.READ_TIMEOUT;

public class ClientFactory {
    private final Environment environment;
    private final ConnectorConfiguration conf;

    private final static String PROXY_HOST_PROPERTY = "https.proxyHost";
    private final static String PROXY_PORT_PROPERTY = "https.proxyPort";

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
                .using(new ApacheConnectorProvider())
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

        SSLConnectionSocketFactory sslConnectionSocketFactory;
        try {
            sslConnectionSocketFactory = new SSLConnectionSocketFactory(
                    SSLContext.getDefault(),
                    new String[]{"TLSv1.2"},
                    null,
                    (HostnameVerifier) null
            );
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Unable to create SSL connection socket factory", e);
        }

        return new InstrumentedHttpClientConnectionManager(
                metricRegistry,
                RegistryBuilder.<ConnectionSocketFactory>create()
                        .register("http", PlainConnectionSocketFactory.getSocketFactory())
                        .register("https", sslConnectionSocketFactory)
                        .build(),
                new ManagedHttpClientConnectionFactory(),
                null,
                SystemDefaultDnsResolver.INSTANCE,
                connectionTimeToLive.toMilliseconds(),
                TimeUnit.MILLISECONDS,
                format("%s.%s", gatewayName, operation)
        );
    }
}

