package uk.gov.pay.connector.app;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.name.Named;
import com.google.inject.persist.jpa.JpaPersistModule;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.setup.Environment;
import org.apache.commons.validator.routines.InetAddressValidator;
import uk.gov.pay.connector.charge.service.Worldpay3dsFlexJwtService;
import uk.gov.pay.connector.charge.util.JwtGenerator;
import uk.gov.pay.connector.common.validator.RequestValidator;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.epdq.EpdqSha512SignatureGenerator;
import uk.gov.pay.connector.gateway.epdq.SignatureGenerator;
import uk.gov.pay.connector.gateway.stripe.StripeSdkClientFactory;
import uk.gov.pay.connector.gateway.stripe.StripeSdkWrapper;
import uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountServicesFactory;
import uk.gov.pay.connector.card.service.CardExecutorService;
import uk.gov.pay.connector.queue.statetransition.StateTransitionQueue;
import uk.gov.pay.connector.usernotification.govuknotify.NotifyClientFactory;
import uk.gov.pay.connector.util.CidrUtils;
import uk.gov.pay.connector.util.HashUtil;
import uk.gov.pay.connector.util.IpAddressMatcher;
import uk.gov.pay.connector.util.JsonObjectMapper;
import uk.gov.pay.connector.util.ReverseDnsLookup;
import uk.gov.pay.connector.wallets.applepay.ApplePayDecrypter;
import uk.gov.service.payments.commons.queue.sqs.SqsQueueService;

import javax.inject.Singleton;
import javax.ws.rs.client.Client;
import java.net.URI;
import java.time.Clock;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static uk.gov.pay.connector.gateway.GatewayOperation.AUTHORISE;
import static uk.gov.pay.connector.gateway.GatewayOperation.CANCEL;
import static uk.gov.pay.connector.gateway.GatewayOperation.CAPTURE;
import static uk.gov.pay.connector.gateway.GatewayOperation.DELETE_STORED_PAYMENT_DETAILS;
import static uk.gov.pay.connector.gateway.GatewayOperation.QUERY;
import static uk.gov.pay.connector.gateway.GatewayOperation.REFUND;
import static uk.gov.pay.connector.gateway.GatewayOperation.VALIDATE_CREDENTIALS;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;

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
        bind(InetAddressValidator.class).in(Singleton.class);

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
    public ReverseDnsLookup reverseDnsLookup() {
        return getReverseDnsLookup();
    }

    protected ReverseDnsLookup getReverseDnsLookup() {
        return new ReverseDnsLookup();
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
    @Named("WorldpayGatewayUrlMap")
    public Map<String, URI> worldpayGatewayUrlMap() {
        return configuration.getGatewayConfigFor(WORLDPAY).getUrls().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, v -> URI.create(v.getValue())));
    }
    
    @Provides
    @Singleton
    @Named("WorldpayCaptureGatewayClient")
    public GatewayClient worldpayCaptureGatewayClient(GatewayClientFactory gatewayClientFactory) {
        return gatewayClientFactory.createGatewayClient(WORLDPAY, CAPTURE, environment.metrics());
    }
    
    @Provides
    @Singleton
    @Named("WorldpayRefundGatewayClient")
    public GatewayClient worldpayRefundGatewayClient(GatewayClientFactory gatewayClientFactory) {
        return gatewayClientFactory.createGatewayClient(WORLDPAY, REFUND, environment.metrics());
    }

    @Provides
    @Singleton
    @Named("WorldpayAuthoriseGatewayClient")
    public GatewayClient worldpayAuthoriseGatewayClient(GatewayClientFactory gatewayClientFactory) {
        return gatewayClientFactory.createGatewayClient(WORLDPAY, AUTHORISE, environment.metrics());
    }

    @Provides
    @Singleton
    @Named("WorldpayCancelGatewayClient")
    public GatewayClient worldpayCancelGatewayClient(GatewayClientFactory gatewayClientFactory) {
        return gatewayClientFactory.createGatewayClient(WORLDPAY, CANCEL, environment.metrics());
    }

    @Provides
    @Singleton
    @Named("WorldpayInquiryGatewayClient")
    public GatewayClient worldpayInquiryGatewayClient(GatewayClientFactory gatewayClientFactory) {
        return gatewayClientFactory.createGatewayClient(WORLDPAY, QUERY, environment.metrics());
    }

    @Provides
    @Singleton
    @Named("WorldpayDeleteTokenGatewayClient")
    public GatewayClient worldpayDeleteTokenGatewayClient(GatewayClientFactory gatewayClientFactory) {
        return gatewayClientFactory.createGatewayClient(WORLDPAY, DELETE_STORED_PAYMENT_DETAILS, environment.metrics());
    }

    @Provides
    @Singleton
    @Named("WorldpayValidateCredentialsGatewayClient")
    public GatewayClient worldpayValidateCredentialsGatewayClient(GatewayClientFactory gatewayClientFactory) {
        return gatewayClientFactory.createGatewayClient(WORLDPAY, VALIDATE_CREDENTIALS, environment.metrics());
    }

    @Provides
    @Singleton
    @Named("AllowedEpdqIpAddresses")
    public Set<String> allowedEpdqIpAddresses(ConnectorConfiguration config) {
        return Set.of();
    }

    @Provides
    @Singleton
    @Named("AllowedSandboxIpAddresses")
    public Set<String> allowedSandboxIpAddresses(ConnectorConfiguration config) {
        return CidrUtils.getIpAddresses(config.getSandboxConfig().getAllowedCidrs());
    }

    @Provides
    @Singleton
    @Named("sandboxAuthToken")
    public String sandboxAuthToken(ConnectorConfiguration config) {
        return config.getSandboxConfig().getSandboxAuthToken();
    }

    @Provides
    @Singleton
    @Named("AllowedStripeIpAddresses")
    public Set<String> allowedStripeIpAddresses(StripeGatewayConfig config) {
        return CidrUtils.getIpAddresses(config.getAllowedCidrs());
    }

    @Provides
    @Singleton
    public IpAddressMatcher ipAddressMatcher(InetAddressValidator inetAddressValidator) {
        return new IpAddressMatcher(inetAddressValidator);
    }
    
    @Provides
    @Singleton
    public WorldpayConfig worldpayConfig() {
        return configuration.getWorldpayConfig();
    }

    @Provides
    public Worldpay3dsFlexJwtService worldpay3dsFlexJwtServiceGenerator() {
        return new Worldpay3dsFlexJwtService(new JwtGenerator(), configuration);
    }

    @Provides
    @Singleton
    public JsonObjectMapper jsonObjectMapper() {
        return new JsonObjectMapper(provideObjectMapper());
    }

    @Provides
    @Singleton
    public Client provideClient() {
        return RestClientFactory.buildClient(configuration.getRestClientConfig(), null);
    }

    @Provides
    @Named("ledgerClient")
    @Singleton
    public Client provideLedgerClient() {
        return RestClientFactory.buildClient(configuration.getRestClientConfig(), configuration.getLedgerPostEventTimeout());
    }

    @Provides
    @Singleton
    public Clock systemUtcClock() {
        return Clock.systemUTC();
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
    public StripeSdkClientFactory stripeSdkClientFactory(ConnectorConfiguration connectorConfiguration) {
        return getStripeSdkClientFactory(connectorConfiguration);
    }

    protected StripeSdkClientFactory getStripeSdkClientFactory(ConnectorConfiguration connectorConfiguration) {
        return new StripeSdkClientFactory(connectorConfiguration, new StripeSdkWrapper());
    }

    @Provides
    @Singleton
    public StateTransitionQueue stateTransitionQueue() {
        return getStateTransitionQueue();
    }

    protected StateTransitionQueue getStateTransitionQueue() {
        return new StateTransitionQueue();
    }

    @Provides
    public AmazonSQS sqsClient(ConnectorConfiguration connectorConfiguration) {

        AmazonSQSClientBuilder clientBuilder = AmazonSQSClientBuilder
                .standard();

        if (connectorConfiguration.getSqsConfig().isNonStandardServiceEndpoint()) {

            BasicAWSCredentials basicAWSCredentials = new BasicAWSCredentials(
                    connectorConfiguration.getSqsConfig().getAccessKey(),
                    connectorConfiguration.getSqsConfig().getSecretKey());

            clientBuilder
                    .withCredentials(new AWSStaticCredentialsProvider(basicAWSCredentials))
                    .withEndpointConfiguration(
                            new AwsClientBuilder.EndpointConfiguration(
                                    connectorConfiguration.getSqsConfig().getEndpoint(),
                                    connectorConfiguration.getSqsConfig().getRegion())
                    );
        } else {
            // uses AWS SDK's DefaultAWSCredentialsProviderChain to obtain credentials
            clientBuilder.withRegion(connectorConfiguration.getSqsConfig().getRegion());
        }

        return clientBuilder.build();
    }

    @Provides
    public SqsQueueService provideSqsQueueService(AmazonSQS amazonSQS, ConnectorConfiguration connectorConfiguration) {
        return new SqsQueueService(
                amazonSQS,
                connectorConfiguration.getSqsConfig().getMessageMaximumWaitTimeInSeconds(),
                connectorConfiguration.getSqsConfig().getMessageMaximumBatchSize());
    }
}
