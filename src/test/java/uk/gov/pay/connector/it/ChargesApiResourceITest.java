package uk.gov.pay.connector.it;

import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.response.ValidatableResponse;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.util.DropwizardAppWithPostgresRule;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static uk.gov.pay.connector.model.ChargeStatus.AUTHORIZATION_SUCCESS;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.LinksAssert.assertNextUrlLink;
import static uk.gov.pay.connector.util.LinksAssert.assertSelfLink;
import static uk.gov.pay.connector.util.NumberMatcher.isNumber;

public class ChargesApiResourceITest {

    public static final String CHARGES_API_PATH = "/v1/api/charges/";
    public static final String FRONTEND_CARD_DETAILS_URL = "/charge/";

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
        String postBody = toJson(ImmutableMap.of(
                "amount", expectedAmount,
                "gateway_account_id", accountId));
        ValidatableResponse response = postCreateChargeResponse(postBody)
                .statusCode(201)
                .body("charge_id", is(notNullValue()))
                .body("amount", isNumber(expectedAmount))
                .contentType(JSON);

        String chargeId = response.extract().path("charge_id");
        String documentLocation = expectedChargeLocationFor(chargeId);

        response.header("Location", is(documentLocation));
        assertSelfLink(response, documentLocation);
        assertNextUrlLink(response, cardDetailsLocationFor(chargeId));

        ValidatableResponse getChargeResponse = getChargeResponseFor(chargeId)
                .statusCode(200)
                .contentType(JSON)
                .body("charge_id", is(chargeId))
                .body("amount", isNumber(expectedAmount))
                .body("status", is("CREATED"));

        assertSelfLink(getChargeResponse, documentLocation);
    }

    @Test
    public void shouldFilterChargeStatusToReturnInProgressIfInternalStatusIsAuthorised() throws Exception {
        String chargeId = ((Integer) RandomUtils.nextInt(99999999)).toString();
        app.getDatabaseTestHelper().addCharge(chargeId, accountId, 500, AUTHORIZATION_SUCCESS);

        getChargeResponseFor(chargeId)
                .statusCode(200)
                .contentType(JSON)
                .body("charge_id", is(chargeId))
                .body("status", is("IN PROGRESS"));
    }

    @Test
    public void cannotMakeChargeForMissingGatewayAccount() throws Exception {
        String missingGatewayAccount = "1234123";
        String postBody = toJson(ImmutableMap.of("amount", 2113, "gateway_account_id", missingGatewayAccount));
        postCreateChargeResponse(postBody)
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

    private String cardDetailsLocationFor(String chargeId) {
        return "http://Frontend" + FRONTEND_CARD_DETAILS_URL + chargeId;
    }
}
