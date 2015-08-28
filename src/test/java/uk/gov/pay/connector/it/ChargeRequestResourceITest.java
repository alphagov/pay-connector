package uk.gov.pay.connector.it;

import com.jayway.restassured.response.ValidatableResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.util.DropwizardAppWithPostgresRule;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static uk.gov.pay.connector.util.NumberMatcher.isNumber;

public class ChargeRequestResourceITest {

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    private String accountId = "72332423443245";

    @Before
    public void setupGatewayAccount() {
        app.getDatabaseTestHelper().addGatewayAccount(accountId, "test gateway");
    }

    @Test
    public void makeChargeAndRetrieveAmount() throws Exception {
        long expectedAmount = 2113l;
        ValidatableResponse response = given().port(app.getLocalPort())
                .contentType(JSON)
                .body(format("{\"amount\":%d, \"gateway_account_id\": \"%s\"}", expectedAmount, accountId))
                .post("/v1/api/charges")
                .then()
                .statusCode(201)
                .contentType(JSON);
        String chargeId = response.extract().path("charge_id");

        String urlSlug = "frontend/charges/" + chargeId;
        response.header("Location", containsString(urlSlug))
                .body("links[0].href", containsString(urlSlug))
                .body("links[0].rel", is("self"))
                .body("links[0].method", is("GET"));

        given().port(app.getLocalPort())
                .get("/v1/frontend/charges/" + chargeId)
                .then()
                .statusCode(200)
                .contentType(JSON)
                .body("charge_id", is(chargeId))
                .body("amount", isNumber(expectedAmount))
                .body("status", is("CREATED"))
                .body("links[0].href", containsString(urlSlug))
                .body("links[0].rel", is("self"))
                .body("links[0].method", is("GET"));
    }

    @Test
    public void cannotMakeChargeForMissingGatewayAccount() throws Exception {
        String missingGatewayAccount = "1234123";
        given().port(app.getLocalPort())
                .contentType(JSON)
                .body(format("{\"amount\":2113, \"gateway_account_id\": \"%s\"}", missingGatewayAccount))
                .post("/v1/api/charges")
                .then()
                .statusCode(400)
                .contentType(JSON)
                .header("Location", is(nullValue()))
                .body("charge_id", is(nullValue()))
                .body("message", is("Unknown gateway account: " + missingGatewayAccount));
    }

    @Test
    public void cannotMakeChargeForMissingFields() throws Exception {
        given().port(app.getLocalPort())
                .contentType(JSON)
                .body("{}")
                .post("/v1/api/charges")
                .then()
                .statusCode(400)
                .contentType(JSON)
                .header("Location", is(nullValue()))
                .body("charge_id", is(nullValue()))
                .body("message", is("Field(s) missing: [amount, gateway_account_id]"));
    }
}