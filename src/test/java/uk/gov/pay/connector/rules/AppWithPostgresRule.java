package uk.gov.pay.connector.rules;

import com.google.inject.Injector;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.testing.ConfigOverride;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.jdbi.v3.core.Jdbi;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.service.payments.commons.testing.db.PostgresDockerRule;
import uk.gov.service.payments.commons.testing.port.PortFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static io.dropwizard.testing.ConfigOverride.config;
import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;
import static java.sql.DriverManager.getConnection;

abstract public class AppWithPostgresRule implements TestRule {
    private static final Logger logger = LoggerFactory.getLogger(AppWithPostgresRule.class);

    private final String configFilePath;
    private final PostgresDockerRule postgres;
    private final AppRule<ConnectorConfiguration> appRule;
    private final RuleChain rules;

    private DatabaseTestHelper databaseTestHelper;
    private final int wireMockPort = PortFactory.findFreePort();

    public AppWithPostgresRule(ConfigOverride... configOverrides) {
        this(true, configOverrides);
    }

    public AppWithPostgresRule(boolean stubPaymentGateways, ConfigOverride... configOverrides) {
        this("config/test-it-config.yaml", stubPaymentGateways, configOverrides);
    }

    public AppWithPostgresRule(String configPath, ConfigOverride... configOverrides) {
        this(configPath, true, configOverrides);
    }

    public AppWithPostgresRule(String configPath, boolean stubPaymentGateways, ConfigOverride... configOverrides) {
        configFilePath = resourceFilePath(configPath);
        postgres = new PostgresDockerRule("15.2");

        ConfigOverride[] newConfigOverrides = overrideDatabaseConfig(configOverrides, postgres);
        newConfigOverrides = overrideSqsConfig(newConfigOverrides);
        newConfigOverrides = overrideUrlsConfig(newConfigOverrides, stubPaymentGateways);
        appRule = newApplication(configFilePath, newConfigOverrides);

        rules = RuleChain.outerRule(postgres).around(appRule);
    }

    abstract protected AppRule<ConnectorConfiguration> newApplication(
            final String configPath,
            final ConfigOverride... configOverrides);

    @Override
    public Statement apply(Statement base, Description description) {
        return rules.apply(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                logger.info("Clearing database.");
                appRule.getApplication().run("db", "drop-all", "--confirm-delete-everything", configFilePath);

                try (Connection connection = getConnection(postgres.getConnectionUrl(), postgres.getUsername(), postgres.getPassword())) {
                    Liquibase migrator = new Liquibase("it-migrations.xml", new ClassLoaderResourceAccessor(), new JdbcConnection(connection));
                    migrator.update("");
                }  catch (LiquibaseException | SQLException e) {
                    throw new PostgresTestDockerException(e);
                }
                restoreDropwizardsLogging();

                DataSourceFactory dataSourceFactory = appRule.getConfiguration().getDataSourceFactory();
                databaseTestHelper = new DatabaseTestHelper(Jdbi.create(dataSourceFactory.getUrl(), dataSourceFactory.getUser(), dataSourceFactory.getPassword()));

                base.evaluate();
            }
        }, description);
    }

    public ConnectorConfiguration getConf() {
        return appRule.getConfiguration();
    }

    public Environment getEnvironment() {
        return appRule.getEnvironment();
    }

    public int getLocalPort() {
        return appRule.getLocalPort();
    }

    public int getAdminPort() {
        return appRule.getAdminPort();
    }

    public int getWireMockPort() {
        return wireMockPort;
    }

    public Injector getGuiceInjector() {
        return appRule.getInjector();
    }

    public <T> T getInstanceFromGuiceContainer(Class<T> type) {
        return appRule.getInstanceFromGuiceContainer(type);
    }

    public DatabaseTestHelper getDatabaseTestHelper() {
        return databaseTestHelper;
    }

    private ConfigOverride[] overrideDatabaseConfig(ConfigOverride[] configOverrides, PostgresDockerRule postgresDockerRule) {
        List<ConfigOverride> newConfigOverride = newArrayList(configOverrides);
        newConfigOverride.add(config("database.url", postgresDockerRule.getConnectionUrl()));
        newConfigOverride.add(config("database.user", postgresDockerRule.getUsername()));
        newConfigOverride.add(config("database.password", postgresDockerRule.getPassword()));
        return newConfigOverride.toArray(new ConfigOverride[0]);
    }
    
    private ConfigOverride[] overrideSqsConfig(ConfigOverride[] configOverrides) {
        List<ConfigOverride> newConfigOverride = newArrayList(configOverrides);
        newConfigOverride.add(config("sqsConfig.captureQueueUrl", "http://localhost:" + wireMockPort + "/capture-queue"));
        return newConfigOverride.toArray(new ConfigOverride[0]);
    }

    private ConfigOverride[] overrideUrlsConfig(ConfigOverride[] configOverrides, boolean stubPaymentGateways) {
        List<ConfigOverride> newConfigOverride = newArrayList(configOverrides);
        if (stubPaymentGateways) {
            newConfigOverride.add(config("worldpay.urls.test", "http://localhost:" + wireMockPort + "/jsp/merchant/xml/paymentService.jsp"));
            newConfigOverride.add(config("worldpay.threeDsFlexDdcUrls.test", String.format("http://localhost:%s/shopper/3ds/ddc.html", wireMockPort)));
            newConfigOverride.add(config("worldpay.threeDsFlexDdcUrls.live", String.format("http://localhost:%s/shopper/3ds/ddc.html", wireMockPort)));
            newConfigOverride.add(config("stripe.url", "http://localhost:" + wireMockPort));
        }
        newConfigOverride.add(config("ledgerBaseURL", "http://localhost:" + wireMockPort));
        newConfigOverride.add(config("cardidBaseURL", "http://localhost:" + wireMockPort));
        return newConfigOverride.toArray(new ConfigOverride[0]);
    }

    private void restoreDropwizardsLogging() {
        appRule.getConfiguration().getLoggingFactory().configure(appRule.getEnvironment().metrics(),
                appRule.getApplication().getName());
    }
}
