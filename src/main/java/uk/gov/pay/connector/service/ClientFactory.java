package uk.gov.pay.connector.service;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.httpclient.InstrumentedHttpClientConnectionManager;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.client.proxy.ProxyConfiguration;
import io.dropwizard.setup.Environment;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.conn.ManagedHttpClientConnectionFactory;
import org.apache.http.impl.conn.SystemDefaultDnsResolver;
import org.glassfish.jersey.SslConfigurator;
import org.glassfish.jersey.apache.connector.ApacheClientProperties;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientProperties;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.OperationOverrides;
import uk.gov.pay.connector.filters.RestClientLoggingFilter;
import uk.gov.pay.connector.filters.XRayHttpClientFilter;
import uk.gov.pay.connector.util.TrustStoreLoader;

import javax.inject.Inject;
import javax.net.ssl.HostnameVerifier;
import javax.ws.rs.client.Client;
import java.util.concurrent.TimeUnit;

import static java.lang.String.*;

public class ClientFactory {
    private final Environment environment;
    private final ConnectorConfiguration conf;

    @Inject
    public ClientFactory(Environment environment, ConnectorConfiguration conf) {
        this.environment = environment;
        this.conf = conf;
    }

    public Client createWithDropwizardClient(PaymentGatewayName gateway, GatewayOperation operation, MetricRegistry metricRegistry) {
        JerseyClientConfiguration clientConfiguration = conf.getClientConfiguration();
        JerseyClientBuilder defaultClientBuilder = new JerseyClientBuilder(environment)
                .using(new ApacheConnectorProvider())
                .using(clientConfiguration)
                .withProperty(ClientProperties.READ_TIMEOUT, getReadTimeoutInMillis(operation, gateway))
                .withProperty(ApacheClientProperties.CONNECTION_MANAGER, createConnectionManager(gateway.getName(), operation.getConfigKey(), metricRegistry));

        // optionally set proxy; see comment below why this has to be done
        if (conf.getCustomJerseyClient().isProxyEnabled()) {
            defaultClientBuilder
                    .withProperty(ClientProperties.PROXY_URI, proxyUrl(clientConfiguration.getProxyConfiguration()));
        }

        Client client = defaultClientBuilder.build(gateway.getName());
        client.register(RestClientLoggingFilter.class).register(XRayHttpClientFilter.class);
        return client;
    }

    private int getReadTimeoutInMillis(GatewayOperation operation, PaymentGatewayName gateway) {
        OperationOverrides overrides = getOverridesFor(operation, gateway);
        if (overrides != null && overrides.getReadTimeout() != null) {
            return (int) overrides.getReadTimeout().toMilliseconds();
        }
        return (int) conf.getCustomJerseyClient().getReadTimeout().toMilliseconds();
    }

    private OperationOverrides getOverridesFor(GatewayOperation operation, PaymentGatewayName gateway) {
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
                                        new String[] { "TLSv1.2" },
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

    /**
     * Constructs the proxy URL required by JerseyClient property ClientProperties.PROXY_URI
     * <p>
     * <b>NOTE:</b> The reason for doing this is, Dropwizard jersey client doesn't seem to work as per
     * http://www.dropwizard.io/0.9.2/docs/manual/configuration.html#proxy where just setting the proxy config in
     * client configuration is only needed. But after several test, that doesn't seem to work, but by setting the
     * native jersey proxy config as per this implementation seems to work
     * <p>
     * similar problem discussed in here -> https://groups.google.com/forum/#!topic/dropwizard-user/AbDSYfLB17M
     * </p>
     * </p>
     *
     * @param proxyConfig from config.yml
     * @return proxy server URL
     */
    private String proxyUrl(ProxyConfiguration proxyConfig) {
        return format("%s://%s:%s",
                proxyConfig.getScheme(),
                proxyConfig.getHost(),
                proxyConfig.getPort()
        );
    }

}

