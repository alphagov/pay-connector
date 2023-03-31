package uk.gov.pay.connector.it.resources;

import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.rules.DropwizardAppRule;

import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.is;

public class HealthCheckResourceIT {

    @Rule
    public DropwizardAppRule<ConnectorConfiguration> app = new DropwizardAppRule<>(
            ConnectorApp.class, resourceFilePath("config/test-it-config.yaml"));

    @Test
    public void healthcheckShouldIdentifyUnhealthyDBAndQueue() {
        given().port(app.getLocalPort())
                .contentType(JSON)
                .get("healthcheck")
                .then()
                .statusCode(503)
                .body("ping.healthy", is(true))
                .body("database.healthy", is(false))
                .body("deadlocks.healthy", is(true))
                .body("sqsQueue.healthy", is(false));
    }
}
