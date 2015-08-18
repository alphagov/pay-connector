package uk.gov.pay.connector.it;

import com.jayway.restassured.response.ValidatableResponse;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.util.DropwizardAppWithPostgresRule;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

public class ChargeRequestTest {

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    @Test
    public void makeChargeAndRetrieveAmount() throws Exception {
        int expectedAmount = 2113;
        ValidatableResponse response = given().port(app.getLocalPort())
                .contentType(JSON)
                .body(String.format("{\"amount\":%d}", expectedAmount))
                .post("/v1/api/charge")
                .then()
                .statusCode(201)
                .contentType(JSON);
        String chargeId = response.extract().path("charge_id");

        response.header("location", containsString("frontend/charge/" + chargeId));

        int amount = given().port(app.getLocalPort())
                .get("/v1/frontend/charge/" + chargeId)
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract()
                .path("amount");

        assertThat(amount, is(expectedAmount));
    }
}
