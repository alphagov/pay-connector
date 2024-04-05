package uk.gov.pay.connector.extension;

import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.inject.Injector;
import com.google.inject.persist.jpa.JpaPersistModule;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.restassured.specification.RequestSpecification;
import org.apache.commons.lang.math.RandomUtils;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.InjectorLookup;
import uk.gov.pay.connector.app.config.AuthorisationConfig;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.rules.LedgerStub;
import uk.gov.pay.connector.rules.SqsTestDocker;
import uk.gov.pay.connector.rules.StripeMockClient;
import uk.gov.pay.connector.rules.WorldpayMockClient;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.util.RestAssuredClient;
import uk.gov.service.payments.commons.testing.port.PortFactory;

import java.util.List;
import java.util.Properties;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.google.common.collect.Lists.newArrayList;
import static io.dropwizard.testing.ConfigOverride.config;
import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static uk.gov.pay.connector.rules.PostgresTestDocker.*;

public class AppWithPostgresAndSqsExtension implements BeforeEachCallback, BeforeAllCallback, AfterEachCallback, AfterAllCallback {

    private static final Logger logger = LoggerFactory.getLogger(AppWithPostgresAndSqsExtension.class);
    private static final String JPA_UNIT = "ConnectorUnit";
    private static String CONFIG_PATH = resourceFilePath("config/test-it-config.yaml");
    private final Jdbi jdbi;
    private final AmazonSQS sqsClient;
    private final DropwizardAppExtension<ConnectorConfiguration> dropwizardAppExtension;
    private Injector injector;
    private final int wireMockPort = PortFactory.findFreePort();
    private final int worldpayWireMockPort = PortFactory.findFreePort();
    private final int ledgerWireMockPort = PortFactory.findFreePort();
    private final int stripeWireMockPort = PortFactory.findFreePort();

    protected static DatabaseTestHelper databaseTestHelper;
    protected static WireMockServer wireMockServer;
    protected static WireMockServer worldpayWireMockServer;
    protected static WireMockServer ledgerWireMockServer;
    protected static WireMockServer stripeWireMockServer;
    protected static WorldpayMockClient worldpayMockClient;
    protected static StripeMockClient stripeMockClient;
    protected static LedgerStub ledgerStub;
    protected static String accountId = String.valueOf(RandomUtils.nextInt());
    protected RestAssuredClient connectorRestApiClient;
    protected static ObjectMapper mapper;
    protected DatabaseFixtures databaseFixtures;

    public AppWithPostgresAndSqsExtension() {
        this(ConnectorApp.class, new ConfigOverride[0]);
    }
    
    public AppWithPostgresAndSqsExtension(Class CustomConnectorClass) {
        this(CustomConnectorClass, new ConfigOverride[0]);
    }
    
    public AppWithPostgresAndSqsExtension(ConfigOverride... configOverrides) {
        this(ConnectorApp.class, configOverrides);
    }

    public AppWithPostgresAndSqsExtension(Class CustomConnectorClass, ConfigOverride... configOverrides) {
        getOrCreate();

        sqsClient = SqsTestDocker.initialise(List.of("capture-queue", "event-queue", "tasks-queue", "reconcile-queue"));

        ConfigOverride[] newConfigOverrides = overrideDatabaseConfig(configOverrides);
        newConfigOverrides = overrideEndpointConfig(newConfigOverrides);
        newConfigOverrides = overrideSqsConfig(newConfigOverrides);

        dropwizardAppExtension = new DropwizardAppExtension<>(CustomConnectorClass,
                CONFIG_PATH, newConfigOverrides);

        try {
            // starts dropwizard application. This is required as we don't use DropwizardExtensionsSupport (which starts application)
            // due to config overrides we need at runtime for database, sqs and any custom configuration needed for tests
            dropwizardAppExtension.before();
        } catch (Exception e) {
            logger.error("Exception starting application - {}", e.getMessage());
            throw new RuntimeException(e);
        }

        jdbi = Jdbi.create(getConnectionUrl(), getDbUsername(), getDbPassword());
        jdbi.installPlugin(new SqlObjectPlugin());
        
        wireMockServer = new WireMockServer(wireMockConfig().port(wireMockPort));
        wireMockServer.start();
        worldpayWireMockServer = new WireMockServer(wireMockConfig().port(worldpayWireMockPort));
        worldpayWireMockServer.start();
        stripeWireMockServer = new WireMockServer(wireMockConfig().port(stripeWireMockPort));
        stripeWireMockServer.start();
        
        databaseTestHelper = new DatabaseTestHelper(jdbi);

        connectorRestApiClient = new RestAssuredClient(getLocalPort(), accountId);

        worldpayMockClient = new WorldpayMockClient(worldpayWireMockServer);
        stripeMockClient = new StripeMockClient(stripeWireMockServer);

        ledgerWireMockServer = new WireMockServer(wireMockConfig().port(ledgerWireMockPort));
        ledgerWireMockServer.start();
        ledgerStub = new LedgerStub(ledgerWireMockServer);
        databaseFixtures = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper);
        mapper = new ObjectMapper();
        
        injector = InjectorLookup.getInjector(dropwizardAppExtension.getApplication()).get();
    }
    
    public void resetWireMockServer() {
        wireMockServer.resetAll();
        worldpayWireMockServer.resetAll();
        ledgerWireMockServer.resetAll();
        stripeWireMockServer.resetAll();
        ledgerStub.acceptPostEvent();
    }
       

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        dropwizardAppExtension.getApplication().run("db", "migrate", CONFIG_PATH);
    }

    @Override
    public void beforeEach(ExtensionContext context) {}
    @Override
    public void afterEach(ExtensionContext context) {}

    @Override
    public void afterAll(ExtensionContext context) {
        databaseTestHelper.truncateAllData();
        wireMockServer.stop();
        worldpayWireMockServer.stop();
        ledgerWireMockServer.stop();
        dropwizardAppExtension.after();
    }

    public RequestSpecification givenSetup() {
        return given().port(getAppRule().getLocalPort())
                .contentType(JSON);
    }

    private ConfigOverride[] overrideDatabaseConfig(ConfigOverride[] configOverrides) {
        List<ConfigOverride> newConfigOverride = newArrayList(configOverrides);
        newConfigOverride.add(config("database.url", getConnectionUrl()));
        newConfigOverride.add(config("database.user", getDbUsername()));
        newConfigOverride.add(config("database.password", getDbPassword()));
        return newConfigOverride.toArray(new ConfigOverride[0]);
    }
    
    private ConfigOverride[] overrideEndpointConfig(ConfigOverride[] configOverrides) {
        List<ConfigOverride> newConfigOverride = newArrayList(configOverrides);
        newConfigOverride.add(config("worldpay.urls.test", "http://localhost:" + worldpayWireMockPort + "/jsp/merchant/xml/paymentService.jsp"));
        newConfigOverride.add(config("worldpay.threeDsFlexDdcUrls.test", "http://localhost:" + worldpayWireMockPort + "/shopper/3ds/ddc.html"));
        newConfigOverride.add(config("stripe.url", "http://localhost:" + stripeWireMockPort));
        newConfigOverride.add(config("ledgerBaseURL", "http://localhost:" + ledgerWireMockPort));
        newConfigOverride.add(config("cardidBaseURL", "http://localhost:" + wireMockPort));
        return newConfigOverride.toArray(new ConfigOverride[0]);
    }

    private JpaPersistModule createJpaModule() {
        final Properties properties = new Properties();
        properties.put("javax.persistence.jdbc.url", getConnectionUrl());
        properties.put("javax.persistence.jdbc.user", getDbUsername());
        properties.put("javax.persistence.jdbc.password", getDbPassword());

        final JpaPersistModule jpaModule = new JpaPersistModule(JPA_UNIT);
        jpaModule.properties(properties);

        return jpaModule;
    }

    private ConfigOverride[] overrideSqsConfig(ConfigOverride[] configOverrides) {
        List<ConfigOverride> newConfigOverride = newArrayList(configOverrides);
        newConfigOverride.add(config("sqsConfig.eventQueueUrl", SqsTestDocker.getQueueUrl("event-queue")));
        newConfigOverride.add(config("sqsConfig.captureQueueUrl", SqsTestDocker.getQueueUrl("capture-queue")));
        newConfigOverride.add(config("sqsConfig.taskQueueUrl", SqsTestDocker.getQueueUrl("tasks-queue")));
        newConfigOverride.add(config("sqsConfig.payoutReconcileQueueUrl", SqsTestDocker.getQueueUrl("reconcile-queue")));
        newConfigOverride.add(config("sqsConfig.endpoint", SqsTestDocker.getEndpoint()));
        return newConfigOverride.toArray(new ConfigOverride[0]);
    }

    public <T> T getInstanceFromGuiceContainer(Class<T> klazz) {
        return injector.getInstance(klazz);
    }

    public AuthorisationConfig getAuthorisationConfig() {
        return dropwizardAppExtension.getConfiguration().getAuthorisationConfig();
    }

    public DropwizardAppExtension<ConnectorConfiguration> getAppRule() {
        return dropwizardAppExtension;
    }

    public int getLocalPort() {
        return dropwizardAppExtension.getLocalPort();
    }

    public Jdbi getJdbi() {
        return jdbi;
    }

    public AmazonSQS getSqsClient() {
        return sqsClient;
    }

    public static WireMockServer getWireMockServer() {
        return wireMockServer;
    }
    
    public static WireMockServer getStripeWireMockServer() {
        return stripeWireMockServer;
    }

    public DatabaseTestHelper getDatabaseTestHelper() {
        return databaseTestHelper;
    }

    public WorldpayMockClient getWorldpayMockClient() {
        return worldpayMockClient;
    }

    public WireMockServer getWorldpayWireMockServer() {
        return worldpayWireMockServer;
    }

    public StripeMockClient getStripeMockClient() {
        return stripeMockClient;
    }

    public LedgerStub getLedgerStub() {
        return ledgerStub;
    }

    public DatabaseFixtures getDatabaseFixtures() {
        return databaseFixtures;
    }

    public String getEventQueueUrl() {
//        return sqsClient.getQueueUrl("event-queue").getQueueUrl();
        return SqsTestDocker.getQueueUrl("event-queue");
    }
    
    public String getTasksQueueUrl() {
        return SqsTestDocker.getQueueUrl("tasks-queue");
    }
}
