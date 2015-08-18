package uk.gov.pay.connector.util;

import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.skife.jdbi.v2.DBI;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.ConnectorConfiguration;

import static io.dropwizard.testing.ConfigOverride.config;
import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;

public class DropwizardAppWithPostgresRule implements TestRule {

    private String configFilePath = resourceFilePath("config/test-it-config.yaml");

    private PostgresDockerRule postgres = new PostgresDockerRule();

    private DropwizardAppRule<ConnectorConfiguration> app = new DropwizardAppRule<>(
            ConnectorApp.class,
            configFilePath,
            config("database.url", postgres.getConnectionUrl()),
            config("database.user", postgres.getUsername()),
            config("database.password", postgres.getPassword()));

    private RuleChain rules = RuleChain.outerRule(postgres).around(app);

    @Override
    public Statement apply(Statement base, Description description) {
        return rules.apply(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                app.getApplication().run("db", "drop-all", "--confirm-delete-everything", configFilePath);
                app.getApplication().run("db", "migrate", configFilePath);
                base.evaluate();
            }
        }, description);
    }

    public DBI getJdbi() {
        ConnectorApp dropwizard = app.getApplication();
        return dropwizard.getJdbi();
    }

    public int getLocalPort() {
        return app.getLocalPort();
    }
}
