package uk.gov.pay.connector.it.resources;

import org.junit.Ignore;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.resources.HealthCheckResource.HEALTHCHECK;

public class HealthCheckResourceITest extends GatewayAccountResourceTestBase {

    @Test
    public void checkHealthcheck_isHealthy() throws Exception {
        givenSetup()
                .get(HEALTHCHECK)
                .then()
                .statusCode(200)
                .body("ping.healthy", is(true))
                .body("database.healthy", is(true))
                .body("deadlocks.healthy", is(true))
                .body("cardExecutorService.healthy", is(true));
    }

    @Test
    public void checkHealthcheck_isUnhealthy() throws Exception {
        app.stopPostgres();
        givenSetup()
                .get(HEALTHCHECK)
                .then()
                .statusCode(503)
                .body("ping.healthy", is(true))
                .body("database.healthy", is(false))
                .body("deadlocks.healthy", is(true))
                .body("cardExecutorService.healthy", is(true));
    }
}
