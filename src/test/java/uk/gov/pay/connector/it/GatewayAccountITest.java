package uk.gov.pay.connector.it;

import com.jayway.restassured.response.ValidatableResponse;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.util.DropwizardAppWithPostgresRule;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.containsString;

public class GatewayAccountITest {

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    @Test
    public void createGatewayAccount() throws Exception {
        String testName = "test account";
        ValidatableResponse response = given().port(app.getLocalPort())
                .contentType(JSON)
                .body(String.format("{\"name\":\"%s\"}", testName))
                .post("/v1/api/gateway")
                .then()
                .statusCode(201)
                .contentType(JSON);
        String accountId = response.extract().path("account_id");

        response.header("Location", containsString("api/gateway/" + accountId));
    }
}
