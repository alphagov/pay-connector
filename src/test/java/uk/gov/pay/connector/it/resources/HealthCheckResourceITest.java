package uk.gov.pay.connector.it.resources;

import org.junit.Test;

import static org.hamcrest.Matchers.is;

public class HealthCheckResourceITest extends GatewayAccountResourceTestBase {

    @Test
    public void checkHealthcheck_isHealthy() {
        givenSetup()
                .get("healthcheck")
                .then()
                .statusCode(200)
                .body("ping.healthy", is(true))
                .body("database.healthy", is(true))
                .body("deadlocks.healthy", is(true))
                .body("cardExecutorService.healthy", is(true));
    }

    @Test
    public void checkHealthcheck_isUnhealthy() {
        app.stopPostgres();
        givenSetup()
                .get("healthcheck")
                .then()
                .statusCode(503)
                .body("ping.healthy", is(true))
                .body("database.healthy", is(false))
                .body("deadlocks.healthy", is(true))
                .body("cardExecutorService.healthy", is(true));
    }
}
