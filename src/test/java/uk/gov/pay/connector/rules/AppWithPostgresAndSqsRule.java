package uk.gov.pay.connector.rules;

import com.amazonaws.services.sqs.AmazonSQS;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.inject.persist.jpa.JpaPersistModule;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.junit.DropwizardAppRule;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.apache.commons.lang3.ArrayUtils;
import org.jdbi.v3.core.Jdbi;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.service.payments.commons.testing.db.PostgresDockerRule;
import uk.gov.service.payments.commons.testing.db.PostgresTestHelper;
import uk.gov.service.payments.commons.testing.port.PortFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.google.common.collect.Lists.newArrayList;
import static io.dropwizard.testing.ConfigOverride.config;
import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;

public class AppWithPostgresAndSqsRule implements TestRule {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppWithPostgresAndSqsRule.class);
    private static final String JPA_UNIT = "ConnectorUnit";

    private final String configFilePath;
    private final PostgresDockerRule postgres;
    private final AmazonSQS sqsClient;
    private final DropwizardAppRule<ConnectorConfiguration> app;
    private final RuleChain rules;
    private DatabaseTestHelper databaseTestHelper;
    private final WireMockServer wireMockServer;
    private final int wireMockPort = PortFactory.findFreePort();

    public AppWithPostgresAndSqsRule(ConfigOverride... configOverrides) {
        this("config/test-it-config.yaml", configOverrides);
    }

    public AppWithPostgresAndSqsRule(String configPath, ConfigOverride... configOverrides) {
        configFilePath = resourceFilePath(configPath);
        postgres = new PostgresDockerRule("15.2");

        sqsClient = SqsTestDocker.initialise(List.of("capture-queue", "event-queue", "tasks-queue"));

        ConfigOverride[] newConfigOverrides = List.of(
                        config("database.url", postgres.getConnectionUrl()),
                        config("database.user", postgres.getUsername()),
                        config("database.password", postgres.getPassword()))
                .toArray(new ConfigOverride[0]);
        newConfigOverrides = overrideSqsConfig(newConfigOverrides);
        newConfigOverrides = overrideUrlsConfig(newConfigOverrides);

        createJpaModule(postgres);

        app = new DropwizardAppRule<>(
                ConnectorApp.class,
                configFilePath,
                ArrayUtils.addAll(newConfigOverrides, configOverrides)
        );
        wireMockServer = new WireMockServer(wireMockConfig().port(wireMockPort));
        wireMockServer.start();
        rules = RuleChain.outerRule(postgres).around(app);
        registerShutdownHook();
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return rules.apply(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                LOGGER.info("Clearing database.");
                app.getApplication().run("db", "drop-all", "--confirm-delete-everything", configFilePath);
                doSecondaryDatabaseMigration();
                restoreDropwizardsLogging();

                DataSourceFactory dataSourceFactory = app.getConfiguration().getDataSourceFactory();
                databaseTestHelper = new DatabaseTestHelper(Jdbi.create(dataSourceFactory.getUrl(), dataSourceFactory.getUser(), dataSourceFactory.getPassword()));

                base.evaluate();
            }
        }, description);
    }

    private void doSecondaryDatabaseMigration() throws SQLException, LiquibaseException {
        try (Connection connection = DriverManager.getConnection(postgres.getConnectionUrl(), postgres.getUsername(), postgres.getPassword())) {
            Liquibase migrator = new Liquibase("it-migrations.xml", new ClassLoaderResourceAccessor(), new JdbcConnection(connection));
            migrator.update("");
        }
    }

    public int getLocalPort() {
        return app.getLocalPort();
    }

    public DatabaseTestHelper getDatabaseTestHelper() {
        return databaseTestHelper;
    }

    public AmazonSQS getSqsClient() {
        return sqsClient;
    }

    public int getWireMockPort() {
        return wireMockPort;
    }

    public WireMockServer getWireMockServer() {
        return wireMockServer;
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(PostgresTestHelper::stop));
    }

    private JpaPersistModule createJpaModule(final PostgresDockerRule postgres) {
        final Properties properties = new Properties();
        properties.put("jakarta.persistence.jdbc.driver", postgres.getDriverClass());
        properties.put("jakarta.persistence.jdbc.url", postgres.getConnectionUrl());
        properties.put("jakarta.persistence.jdbc.user", postgres.getUsername());
        properties.put("jakarta.persistence.jdbc.password", postgres.getPassword());

        final JpaPersistModule jpaModule = new JpaPersistModule(JPA_UNIT);
        jpaModule.properties(properties);

        return jpaModule;
    }

    private void restoreDropwizardsLogging() {
        app.getConfiguration().getLoggingFactory().configure(app.getEnvironment().metrics(),
                app.getApplication().getName());
    }

    private ConfigOverride[] overrideDBConfig(ConfigOverride[] configOverrides) {
        List<ConfigOverride> newConfigOverride = newArrayList(configOverrides);
        newConfigOverride.add(config("database.url", postgres.getConnectionUrl()));
        newConfigOverride.add(config("database.user", postgres.getUsername()));
        newConfigOverride.add(config("database.password", postgres.getPassword()));
        return newConfigOverride.toArray(new ConfigOverride[0]);
    }

    private ConfigOverride[] overrideSqsConfig(ConfigOverride[] configOverrides) {
        List<ConfigOverride> newConfigOverride = newArrayList(configOverrides);
        newConfigOverride.add(config("sqsConfig.captureQueueUrl", SqsTestDocker.getQueueUrl("capture-queue")));
        newConfigOverride.add(config("sqsConfig.eventQueueUrl", SqsTestDocker.getQueueUrl("event-queue")));
        newConfigOverride.add(config("sqsConfig.taskQueueUrl", SqsTestDocker.getQueueUrl("tasks-queue")));
        newConfigOverride.add(config("sqsConfig.endpoint", SqsTestDocker.getEndpoint()));
        return newConfigOverride.toArray(new ConfigOverride[0]);
    }

    private ConfigOverride[] overrideUrlsConfig(ConfigOverride[] configOverrides) {
        List<ConfigOverride> newConfigOverride = newArrayList(configOverrides);
        newConfigOverride.add(config("worldpay.urls.test", "http://localhost:" + wireMockPort + "/jsp/merchant/xml/paymentService.jsp"));
        newConfigOverride.add(config("worldpay.threeDsFlexDdcUrls.test", "http://localhost:" + wireMockPort + "/shopper/3ds/ddc.html"));
        newConfigOverride.add(config("stripe.url", "http://localhost:" + wireMockPort));
        newConfigOverride.add(config("ledgerBaseURL", "http://localhost:" + wireMockPort));
        newConfigOverride.add(config("cardidBaseURL", "http://localhost:" + wireMockPort));
        return newConfigOverride.toArray(new ConfigOverride[0]);
    }
}
