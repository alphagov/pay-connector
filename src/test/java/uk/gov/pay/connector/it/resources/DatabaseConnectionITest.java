package uk.gov.pay.connector.it.resources;

import org.junit.Test;
import uk.gov.pay.connector.it.base.ChargingITestBase;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

public class DatabaseConnectionITest extends ChargingITestBase {

    public DatabaseConnectionITest() {
        super("sandbox");
    }

    @Test
    public void testDatabaseHealthcheckWhenDatabaseIsUp() {
        given().port(app.getAdminPort())
                .get("/healthcheck")
                .then()
                .statusCode(200)
                .body("database.healthy", is(true));
    }

    @Test
    public void testDatabaseHealthcheckWhenDatabaseIsDown() {
        app.stopPostgres();
        given().port(app.getAdminPort())
                .get("/healthcheck")
                .then()
                .statusCode(500)
                .body("database.healthy", is(false));
    }
}
