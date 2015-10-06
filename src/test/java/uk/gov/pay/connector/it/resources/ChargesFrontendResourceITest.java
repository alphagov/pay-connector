package uk.gov.pay.connector.it.resources;

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
import static javax.ws.rs.HttpMethod.POST;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.LinksAssert.assertLink;
import static uk.gov.pay.connector.util.LinksAssert.assertSelfLink;
import static uk.gov.pay.connector.util.NumberMatcher.isNumber;

public class ChargesFrontendResourceITest {

    public static final String CHARGES_API_PATH = "/v1/api/charges/";
    public static final String CHARGES_FRONTEND_PATH = "/v1/frontend/charges/";

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    private String accountId = "72332423443245";
    private String returnUrl = "http://whatever.com";

    @Before
    public void setupGatewayAccount() {
        app.getDatabaseTestHelper().addGatewayAccount(accountId, "test gateway");
    }

    @Test
    public void getChargeShouldIncludeCardAuthAndCardCaptureLinkButNotGatewayAccountId() throws Exception {
        long expectedAmount = 2113l;
        String postBody = toJson(ImmutableMap.of(
                "amount", expectedAmount,
                "gateway_account_id", accountId,
                "return_url", returnUrl));
        ValidatableResponse response = postCreateChargeResponse(postBody)
                .statusCode(201)
                .body("charge_id", is(notNullValue()))
                .body("amount", isNumber(expectedAmount))
                .body("return_url", is(returnUrl))
                .contentType(JSON);

        String chargeId = response.extract().path("charge_id");

        ValidatableResponse getChargeResponse = getChargeResponseFor(chargeId)
                .statusCode(200)
                .contentType(JSON)
                .body("charge_id", is(chargeId))
                .body("amount", isNumber(expectedAmount))
                .body("containsKey('gateway_account_id')", is(false))
                .body("status", is("CREATED"))
                .body("return_url", is(returnUrl));

        String documentLocation = expectedChargeLocationFor(chargeId);
        assertSelfLink(getChargeResponse, documentLocation);

        String cardAuthUrl = expectedCardAuthUrlFor(chargeId);
        assertLink(getChargeResponse, "cardAuth", POST, cardAuthUrl);

        String cardCaptureUrl = expectedCardCaptureUrlFor(chargeId);
        assertLink(getChargeResponse, "cardCapture", POST, cardCaptureUrl);
    }

    @Test
    public void shouldReturnInternalChargeStatusIfInternalStatusIsAuthorised() throws Exception {
        String chargeId = ((Integer) RandomUtils.nextInt(99999999)).toString();
        app.getDatabaseTestHelper().addCharge(chargeId, accountId, 500, AUTHORISATION_SUCCESS, returnUrl, null);

        getChargeResponseFor(chargeId)
                .statusCode(200)
                .contentType(JSON)
                .body("charge_id", is(chargeId))
                .body("status", is(AUTHORISATION_SUCCESS.getValue()));
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
                .get(CHARGES_FRONTEND_PATH + chargeId)
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
        return "http://localhost:" + app.getLocalPort() + CHARGES_FRONTEND_PATH + chargeId;
    }

    private String expectedCardAuthUrlFor(String chargeId) {
        return "http://localhost:" + app.getLocalPort() + CHARGES_FRONTEND_PATH + chargeId + "/cards";
    }

    private String expectedCardCaptureUrlFor(String chargeId) {
        return "http://localhost:" + app.getLocalPort() + CHARGES_FRONTEND_PATH + chargeId + "/capture";
    }
}