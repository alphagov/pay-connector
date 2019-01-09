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
import org.glassfish.jersey.SslConfigurator;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientProperties;
import uk.gov.pay.commons.utils.xray.XRayHttpClientFilter;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.OperationOverrides;
import uk.gov.pay.connector.filters.RestClientLoggingFilter;
import uk.gov.pay.connector.util.TrustStoreLoader;

import javax.inject.Inject;
import javax.net.ssl.HostnameVerifier;
import javax.ws.rs.client.Client;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static org.glassfish.jersey.apache.connector.ApacheClientProperties.CONNECTION_MANAGER;
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
        return createWithDropwizardClient(gateway, Optional.of(operation), metricRegistry);
    }

    public Client createWithDropwizardClient(PaymentGatewayName gateway, MetricRegistry metricRegistry) {
        return createWithDropwizardClient(gateway, Optional.empty(), metricRegistry);
    }

    private Client createWithDropwizardClient(PaymentGatewayName gateway, Optional<GatewayOperation> operation, MetricRegistry metricRegistry) {
        JerseyClientBuilder defaultClientBuilder = new JerseyClientBuilder(environment)
                .using(new ApacheConnectorProvider())
                .using(conf.getClientConfiguration())
                .withProperty(READ_TIMEOUT, (int) getReadTimeout(operation.orElse(null), gateway).toMilliseconds())
                .withProperty(CONNECTION_MANAGER, createConnectionManager(
                        gateway.getName(),
                        operation.map(GatewayOperation::getConfigKey).orElse("all"),
                        metricRegistry));

        if (System.getProperty(PROXY_HOST_PROPERTY) != null && System.getProperty(PROXY_PORT_PROPERTY) != null) {
            defaultClientBuilder.withProperty(ClientProperties.PROXY_URI, format("http://%s:%s",
                    System.getProperty(PROXY_HOST_PROPERTY), System.getProperty(PROXY_PORT_PROPERTY))
            );
        }

        Client client = defaultClientBuilder.build(gateway.getName());
        client.register(RestClientLoggingFilter.class);

        if (conf.isXrayEnabled()) client.register(XRayHttpClientFilter.class);

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

    private HttpClientConnectionManager createConnectionManager(String gatewayName, String operation, MetricRegistry metricRegistry) {
        return new InstrumentedHttpClientConnectionManager(
                metricRegistry,
                RegistryBuilder.<ConnectionSocketFactory>create()
                        .register("http", PlainConnectionSocketFactory.getSocketFactory())
                        .register("https",
                                new SSLConnectionSocketFactory(
                                        SslConfigurator
                                                .newInstance()
                                                .trustStore(TrustStoreLoader.getTrustStore())
                                                .createSSLContext(),
                                        new String[]{"TLSv1.2"},
                                        null,
                                        (HostnameVerifier) null
                                )
                        )
                        .build(),
                new ManagedHttpClientConnectionFactory(),
                null,
                SystemDefaultDnsResolver.INSTANCE,
                -1,
                TimeUnit.MILLISECONDS,
                format("%s.%s", gatewayName, operation)
        );
    }
}

