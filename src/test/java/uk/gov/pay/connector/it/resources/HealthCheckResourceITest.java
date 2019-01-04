package uk.gov.pay.connector.it.resources;

import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.is;

public class HealthCheckResourceITest {

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();
    
    @Test
    public void checkHealthcheck_isHealthy() {
        given().port(app.getLocalPort())
                .contentType(JSON)
                .get("healthcheck")
                .then()
                .statusCode(200)
                .body("ping.healthy", is(true))
                .body("database.healthy", is(true))
                .body("deadlocks.healthy", is(true));
    }

    @Test
    public void checkHealthcheck_isUnhealthy() {
        app.stopPostgres();
        given().port(app.getLocalPort())
                .contentType(JSON)
                .get("healthcheck")
                .then()
                .statusCode(503)
                .body("ping.healthy", is(true))
                .body("database.healthy", is(false))
                .body("deadlocks.healthy", is(true));
    }
}
