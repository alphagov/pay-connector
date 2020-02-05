package uk.gov.pay.connector.it.resources;

import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class ExpungeResourceIT {

    @DropwizardTestContext
    protected TestContext testContext;

    public ExpungeResourceIT() {
    }

    @Test
    public void shouldExpireChargesBeforeAndAfterAuthorisationAndShouldHaveTheRightEvents() {

        given().port(testContext.getPort())
                .contentType(JSON)
                .post("/v1/tasks/expunge")
                .then()
                .statusCode(200);

        // TODO: in PP-6098 
        // insert charges and assert that the charge has been expunged or marked as parity checked
    }

}
