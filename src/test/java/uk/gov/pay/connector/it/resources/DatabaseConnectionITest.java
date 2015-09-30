package uk.gov.pay.connector.it.resources;

import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.util.PostgresDockerRule;

import static com.jayway.restassured.RestAssured.given;
import static io.dropwizard.testing.ConfigOverride.config;
import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;
import static org.hamcrest.Matchers.is;

public class DatabaseConnectionITest {

    private PostgresDockerRule postgres = new PostgresDockerRule();

    private DropwizardAppRule<ConnectorConfiguration> app = new DropwizardAppRule<>(
            ConnectorApp.class,
            resourceFilePath("config/test-it-config.yaml"),
            config("database.url", postgres.getConnectionUrl()),
            config("database.user", postgres.getUsername()),
            config("database.password", postgres.getPassword()));

    @Rule
    public RuleChain rules = RuleChain.outerRule(postgres).around(app);

    @Test
    public void testDatabaseHealthcheckWhenDatabaseIsUp() {
        given().port(app.getAdminPort())
                .get("/healthcheck")
                .then()
                .body("database.healthy", is(true));
    }

    @Test
    public void testDatabaseHealthcheckWhenDatabaseIsDown() {
        postgres.stop();
        given().port(app.getAdminPort())
                .get("/healthcheck")
                .then()
                .body("database.healthy", is(false));
    }
}
