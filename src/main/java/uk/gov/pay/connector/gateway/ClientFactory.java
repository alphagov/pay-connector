package uk.gov.pay.connector.gateway;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.httpclient.InstrumentedHttpClientConnectionManager;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.setup.Environment;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.conn.ManagedHttpClientConnectionFactory;
import org.apache.http.impl.conn.SystemDefaultDnsResolver;
import org.glassfish.jersey.SslConfigurator;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
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
        JerseyClientConfiguration clientConfiguration = conf.getClientConfiguration();
        JerseyClientBuilder defaultClientBuilder = new JerseyClientBuilder(environment)
                .using(new ApacheConnectorProvider())
                .using(clientConfiguration);

        if (operation.isPresent()) {
            defaultClientBuilder
                    .withProperty(READ_TIMEOUT, getReadTimeoutInMillis(operation.get(), gateway))
                    .withProperty(CONNECTION_MANAGER, createConnectionManager(gateway.getName(), operation.get().getConfigKey(), metricRegistry));
        } else {
            defaultClientBuilder
                    .withProperty(READ_TIMEOUT, getDefaultTimeout())
                    .withProperty(CONNECTION_MANAGER, createConnectionManager(gateway.getName(), "all", metricRegistry));
        }

        Client client = defaultClientBuilder.build(gateway.getName());
        client.register(RestClientLoggingFilter.class);

        if (conf.isXrayEnabled()) client.register(XRayHttpClientFilter.class);

        return client;
    }

    private int getReadTimeoutInMillis(GatewayOperation operation, PaymentGatewayName gateway) {
        OperationOverrides overrides = getOverridesFor(operation, gateway);
        if (overrides != null && overrides.getReadTimeout() != null) {
            return (int) overrides.getReadTimeout().toMilliseconds();
        }
        return getDefaultTimeout();
    }

    private int getDefaultTimeout() {
        return (int) conf.getCustomJerseyClient().getReadTimeout().toMilliseconds();
    }

    private OperationOverrides getOverridesFor(GatewayOperation operation, PaymentGatewayName gateway) {
        if (gateway.equals(PaymentGatewayName.STRIPE)) return null;
        return conf.getGatewayConfigFor(gateway)
                .getJerseyClientOverrides()
                .map(jerseyClientOverrides -> jerseyClientOverrides.getOverridesFor(operation))
                .orElse(null);
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

