package uk.gov.pay.connector.app;

import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.persist.jpa.JpaPersistModule;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.setup.Environment;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.validator.RequestValidator;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.epdq.EpdqSha512SignatureGenerator;
import uk.gov.pay.connector.gateway.epdq.SignatureGenerator;
import uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountServicesFactory;
import uk.gov.pay.connector.paymentprocessor.service.CardExecutorService;
import uk.gov.pay.connector.usernotification.govuknotify.NotifyClientFactory;
import uk.gov.pay.connector.util.HashUtil;
import uk.gov.pay.connector.util.JsonObjectMapper;
import uk.gov.pay.connector.util.XrayUtils;
import uk.gov.pay.connector.wallets.applepay.ApplePayDecrypter;

import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import static org.apache.commons.lang3.StringUtils.isEmpty;

public class ConnectorModule extends AbstractModule {
    final ConnectorConfiguration configuration;
    final Environment environment;

    public ConnectorModule(final ConnectorConfiguration configuration, final Environment environment) {
        this.configuration = configuration;
        this.environment = environment;
    }

    @Override
    protected void configure() {
        bind(ConnectorConfiguration.class).toInstance(configuration);
        bind(Environment.class).toInstance(environment);
        bind(CardExecutorService.class).in(Singleton.class);
        bind(ApplePayDecrypter.class).in(Singleton.class);
        bind(PaymentProviders.class).in(Singleton.class);
        bind(HashUtil.class);
        bind(RequestValidator.class);
        bind(GatewayAccountRequestValidator.class).in(Singleton.class);

        install(jpaModule(configuration));
        install(new FactoryModuleBuilder().build(GatewayAccountServicesFactory.class));
    }

    private JpaPersistModule jpaModule(ConnectorConfiguration configuration) {
        DataSourceFactory dbConfig = configuration.getDataSourceFactory();

        final Properties properties = new Properties();
        properties.put("javax.persistence.jdbc.driver", dbConfig.getDriverClass());
        properties.put("javax.persistence.jdbc.url", dbConfig.getUrl());
        properties.put("javax.persistence.jdbc.user", dbConfig.getUser());
        properties.put("javax.persistence.jdbc.password", dbConfig.getPassword());

        JPAConfiguration jpaConfiguration = configuration.getJpaConfiguration();
        properties.put("eclipselink.logging.level", jpaConfiguration.getJpaLoggingLevel());
        properties.put("eclipselink.logging.level.sql", jpaConfiguration.getSqlLoggingLevel());
        properties.put("eclipselink.query-results-cache", jpaConfiguration.getCacheSharedDefault());
        properties.put("eclipselink.cache.shared.default", jpaConfiguration.getCacheSharedDefault());
        properties.put("eclipselink.ddl-generation.output-mode", jpaConfiguration.getDdlGenerationOutputMode());

        if (configuration.isXrayEnabled()) {
            properties.put("eclipselink.session.customizer", "uk.gov.pay.connector.util.ConnectorSessionCustomiserWithXrayProfiling");
        } else {
            properties.put("eclipselink.session.customizer", "uk.gov.pay.connector.util.ConnectorSessionCustomiser");
        }

        final JpaPersistModule jpaModule = new JpaPersistModule("ConnectorUnit");
        jpaModule.properties(properties);

        return jpaModule;
    }

    @Provides
    public ObjectMapper provideObjectMapper() {
        return environment.getObjectMapper();
    }

    @Provides
    @Singleton
    public XrayUtils xrayUtils(ConnectorConfiguration connectorConfiguration) {
        return new XrayUtils(connectorConfiguration.isXrayEnabled());
    }

    @Provides
    public SignatureGenerator signatureGenerator() {
        return new EpdqSha512SignatureGenerator();
    }

    @Provides
    public StripeGatewayConfig stripeGatewayConfig(ConnectorConfiguration connectorConfiguration) {
        return connectorConfiguration.getStripeConfig();
    }

    @Provides
    @Singleton
    public JsonObjectMapper jsonObjectMapper() {
        return new JsonObjectMapper(provideObjectMapper());
    }

    @Provides
    @Singleton
    public NotifyClientFactory notifyClientFactory(ConnectorConfiguration connectorConfiguration) {
        return getNotifyClientFactory(connectorConfiguration);
    }

    protected NotifyClientFactory getNotifyClientFactory(ConnectorConfiguration connectorConfiguration) {
        return new NotifyClientFactory(connectorConfiguration);
    }

    @Provides
    @Singleton
    public Queue<ChargeEntity> captureQueue(ConnectorConfiguration connectorConfiguration) {
        return new ArrayBlockingQueue(connectorConfiguration.getCaptureProcessConfig().getBatchSize());
    }

    @Provides
    public AmazonSQS sqsClient(ConnectorConfiguration connectorConfiguration) {

        if (isEmpty(connectorConfiguration.getSqsConfig().getEndpoint())) {
            return AmazonSQSClientBuilder.standard()
                    .withCredentials(new InstanceProfileCredentialsProvider(false))
                    .withRegion(connectorConfiguration.getSqsConfig().getRegion())
                    .build();
        } else {
            
            return AmazonSQSClientBuilder.standard()
                    .withEndpointConfiguration(
                            new AwsClientBuilder.EndpointConfiguration(
                                    connectorConfiguration.getSqsConfig().getEndpoint(),
                                    connectorConfiguration.getSqsConfig().getRegion())
                    ).build();
        }
        
    }
}
