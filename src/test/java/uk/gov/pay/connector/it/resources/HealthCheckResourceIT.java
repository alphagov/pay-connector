package uk.gov.pay.connector.it.resources;

import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.is;

public class HealthCheckResourceIT {

    @Rule
    public final io.dropwizard.testing.junit.DropwizardAppRule<ConnectorConfiguration> RULE =
            new DropwizardAppRule<>(ConnectorApp.class, ResourceHelpers.resourceFilePath("config/test-config.yaml"));

    @Test
    public void healthcheckShouldIdentifyUnhealthyDBAndQueue() {
//        app.stopPostgres();
        given().port(RULE.getLocalPort())
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
