package uk.gov.pay.connector.it.resources;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.is;

public class HealthCheckResourceIT {
    @RegisterExtension
    public static ITestBaseExtension app = new ITestBaseExtension("sandbox");


    @Test
    void healthcheckShouldIdentifyHealthyDBAndQueue() {
        given().port(app.getLocalPort())
                .contentType(JSON)
                .get("healthcheck")
                .then()
                .statusCode(200)
                .body("ping.healthy", is(true))
                .body("database.healthy", is(true))
                .body("deadlocks.healthy", is(true))
                .body("sqsQueue.healthy", is(true));
    }
}
