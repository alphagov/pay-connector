package uk.gov.pay.connector.rules;

import com.google.inject.Injector;
import com.spotify.docker.client.exceptions.DockerException;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.ConfigOverride;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static io.dropwizard.testing.ConfigOverride.config;
import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;

abstract public class AppWithPostgresRule implements TestRule {
    private static final Logger logger = LoggerFactory.getLogger(AppWithPostgresRule.class);

    private final String configFilePath;
    private final PostgresDockerRule postgres;
    private final AppRule<ConnectorConfiguration> appRule;
    private final RuleChain rules;

    private DatabaseTestHelper databaseTestHelper;

    public AppWithPostgresRule(ConfigOverride... configOverrides) {
        this("config/test-it-config.yaml", configOverrides);
    }

    public AppWithPostgresRule(String configPath, ConfigOverride... configOverrides) {
        configFilePath = resourceFilePath(configPath);
        try {
            postgres = new PostgresDockerRule();
        } catch (DockerException e) {
            throw new RuntimeException(e);
        }

        appRule = newApplication(configFilePath, overrideDatabaseConfig(configOverrides, postgres));

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
                appRule.getApplication().run("db", "migrate", configFilePath);

                restoreDropwizardsLogging();

                DataSourceFactory dataSourceFactory = appRule.getConfiguration().getDataSourceFactory();
                databaseTestHelper = new DatabaseTestHelper(new DBI(dataSourceFactory.getUrl(), dataSourceFactory.getUser(), dataSourceFactory.getPassword()));

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

    public Injector getGuiceInjector() {
        return appRule.getInjector();
    }

    public <T> T getInstanceFromGuiceContainer(Class<T> type) {
        return appRule.getInstanceFromGuiceContainer(type);
    }

    public DatabaseTestHelper getDatabaseTestHelper() {
        return databaseTestHelper;
    }

    public void stopPostgres() {
        postgres.stop();
    }

    private ConfigOverride[] overrideDatabaseConfig(ConfigOverride[] configOverrides, PostgresDockerRule postgresDockerRule) {
        List<ConfigOverride> newConfigOverride = newArrayList(configOverrides);
        newConfigOverride.add(config("database.url", postgresDockerRule.getConnectionUrl()));
        newConfigOverride.add(config("database.user", postgresDockerRule.getUsername()));
        newConfigOverride.add(config("database.password", postgresDockerRule.getPassword()));
        return newConfigOverride.toArray(new ConfigOverride[0]);
    }

    private void restoreDropwizardsLogging() {
        appRule.getConfiguration().getLoggingFactory().configure(appRule.getEnvironment().metrics(),
                appRule.getApplication().getName());
    }
}
