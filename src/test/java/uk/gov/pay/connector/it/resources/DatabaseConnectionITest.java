package uk.gov.pay.connector.it.resources;

import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresAndSqsRule;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

public class DatabaseConnectionITest {

    @Rule
    public DropwizardAppWithPostgresAndSqsRule app = new DropwizardAppWithPostgresAndSqsRule();

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
