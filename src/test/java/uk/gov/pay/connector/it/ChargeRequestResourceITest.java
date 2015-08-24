package uk.gov.pay.connector.it;

import com.jayway.restassured.response.ValidatableResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.util.DropwizardAppWithPostgresRule;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class ChargeRequestResourceITest {

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    private long accountId = 72332423443245l;

    @Before
    public void setupGatewayAccount() {
        app.getDatabaseTestHelper().addGatewayAccount(accountId, "test gateway");
    }

    @Test
    public void makeChargeAndRetrieveAmount() throws Exception {
        int expectedAmount = 2113;
        ValidatableResponse response = given().port(app.getLocalPort())
                .contentType(JSON)
                .body(String.format("{\"amount\":%d, \"gateway_account\": %s}", expectedAmount, accountId))
                .post("/v1/api/charges")
                .then()
                .statusCode(201)
                .contentType(JSON);
        String chargeId = response.extract().path("charge_id");

        response.header("Location", containsString("frontend/charges/" + chargeId));

        given().port(app.getLocalPort())
                .get("/v1/frontend/charges/" + chargeId)
                .then()
                .statusCode(200)
                .contentType(JSON)
                .body("amount", is(expectedAmount))
                .body("status", is("CREATED"))
                ;
    }

    @Test
    public void cannotMakeChargeForMissingGatewayAccount() throws Exception {
        Long missingGatewayAccount = 1L;
        given().port(app.getLocalPort())
                .contentType(JSON)
                .body(String.format("{\"amount\":2113, \"gateway_account\": %s}", missingGatewayAccount))
                .post("/v1/api/charges")
                .then()
                .statusCode(400)
                .contentType(JSON)
                .header("Location", is(nullValue()))
                .body("charge_id", is(nullValue()))
                .body("message", is("Unknown gateway account: 1"));
    }

}
