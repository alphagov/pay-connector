package uk.gov.pay.connector.rules;

import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import static io.dropwizard.testing.ConfigOverride.config;
import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;

public class DropwizardAppWithPostgresRule implements TestRule {
    private static final Logger logger = LoggerFactory.getLogger(DropwizardAppWithPostgresRule.class);

    private final String configFilePath;
    private final PostgresDockerRule postgres = new PostgresDockerRule();
    private final DropwizardAppRule<ConnectorConfiguration> app;
    private final RuleChain rules;
    private DatabaseTestHelper databaseTestHelper;

    public DropwizardAppWithPostgresRule() {
        this("config/test-it-config.yaml");
    }

    public DropwizardAppWithPostgresRule(String configPath) {
        configFilePath = resourceFilePath(configPath);
        app = new DropwizardAppRule<>(
                ConnectorApp.class,
                configFilePath,
                config("database.url", postgres.getConnectionUrl()),
                config("database.user", postgres.getUsername()),
                config("database.password", postgres.getPassword()));
        rules = RuleChain.outerRule(postgres).around(app);
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return rules.apply(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                logger.info("Clearing database.");
                app.getApplication().run("db", "drop-all", "--confirm-delete-everything", configFilePath);
                app.getApplication().run("db", "migrate", configFilePath);

                restoreDropwizardsLogging();

                databaseTestHelper = new DatabaseTestHelper(getJdbi());

                base.evaluate();
            }
        }, description);
    }

    public ConnectorConfiguration getConf() {
        return app.getConfiguration();
    }

    public DBI getJdbi() {
        ConnectorApp dropwizard = app.getApplication();
        return dropwizard.getJdbi();
    }

    public int getLocalPort() {
        return app.getLocalPort();
    }

    public DatabaseTestHelper getDatabaseTestHelper() {
        return databaseTestHelper;
    }

    private void restoreDropwizardsLogging() {
        app.getConfiguration().getLoggingFactory().configure(app.getEnvironment().metrics(),
                app.getApplication().getName());
    }
}
