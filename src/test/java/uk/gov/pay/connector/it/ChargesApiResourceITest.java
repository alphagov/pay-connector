package uk.gov.pay.connector.it;

import com.jayway.restassured.response.ValidatableResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.util.DropwizardAppWithPostgresRule;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static uk.gov.pay.connector.util.LinksAssert.assertSelfLink;
import static uk.gov.pay.connector.util.NumberMatcher.isNumber;

public class ChargesApiResourceITest {

    public static final String CHARGES_API_PATH = "/v1/api/charges/";
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
        String postBody = format("{\"amount\":%d, \"gateway_account_id\": \"%s\"}", expectedAmount, accountId);
        ValidatableResponse response = postCreateChargeResponse(postBody)
                .statusCode(201)
                .contentType(JSON);

        String chargeId = response.extract().path("charge_id");
        String documentLocation = expectedChargeLocationFor(chargeId);

        response.header("Location", is(documentLocation));
        assertSelfLink(response, documentLocation);

        ValidatableResponse getChargeResponse = getChargeResponseFor(chargeId)
                .statusCode(200)
                .contentType(JSON)
                .body("charge_id", is(chargeId))
                .body("amount", isNumber(expectedAmount))
                .body("status", is("CREATED"));

        assertSelfLink(getChargeResponse, documentLocation);
    }

    @Test
    public void cannotMakeChargeForMissingGatewayAccount() throws Exception {
        String missingGatewayAccount = "1234123";
        postCreateChargeResponse(format("{\"amount\":2113, \"gateway_account_id\": \"%s\"}", missingGatewayAccount))
                .statusCode(400)
                .contentType(JSON)
                .header("Location", is(nullValue()))
                .body("charge_id", is(nullValue()))
                .body("message", is("Unknown gateway account: " + missingGatewayAccount));
    }

    @Test
    public void cannotMakeChargeForMissingFields() throws Exception {
        postCreateChargeResponse("{}")
                .statusCode(400)
                .contentType(JSON)
                .header("Location", is(nullValue()))
                .body("charge_id", is(nullValue()))
                .body("message", is("Field(s) missing: [amount, gateway_account_id]"));
    }

    @Test
    public void cannotGetCharge_WhenInvalidChargeId() throws Exception {
        String chargeId = "23235124";
        getChargeResponseFor(chargeId)
                .statusCode(404)
                .contentType(JSON)
                .body("message", is(format("Charge with id [%s] not found.", chargeId)));
    }

    private ValidatableResponse getChargeResponseFor(String chargeId) {
        return given().port(app.getLocalPort())
                .get(CHARGES_API_PATH + chargeId)
                .then();
    }

    private ValidatableResponse postCreateChargeResponse(String postBody) {
        return given().port(app.getLocalPort())
                .contentType(JSON)
                .body(postBody)
                .post(CHARGES_API_PATH)
                .then();
    }

    private String expectedChargeLocationFor(String chargeId) {
        return "http://localhost:" + app.getLocalPort() + CHARGES_API_PATH + chargeId;
    }
}