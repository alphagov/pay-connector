package uk.gov.pay.connector;

import io.dropwizard.Configuration;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.app.ConnectorApp;

import static com.jayway.restassured.RestAssured.given;
import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;
import static org.hamcrest.Matchers.is;

public class PingTest {

    @Rule
    public DropwizardAppRule<Configuration> app = new DropwizardAppRule<>(ConnectorApp.class, resourceFilePath("config/test-config.yaml"));

    @Test
    public void testPing() {
        given().port(app.getAdminPort())
                .get("/healthcheck")
                .then()
                .body("ping.healthy", is(true));
    }
}
