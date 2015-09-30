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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.LinksAssert.assertNextUrlLink;
import static uk.gov.pay.connector.util.LinksAssert.assertSelfLink;
import static uk.gov.pay.connector.util.NumberMatcher.isNumber;

public class ChargesApiResourceITest {

    private static final String CHARGES_API_PATH = "/v1/api/charges/";
    private static final String FRONTEND_CARD_DETAILS_URL = "/charge/";

    private static final String JSON_AMOUNT_KEY = "amount";
    private static final String JSON_GATEWAY_ACC_KEY = "gateway_account_id";
    private static final String JSON_RETURN_URL_KEY = "return_url";
    private static final String JSON_CHARGE_KEY = "charge_id";
    private static final String JSON_STATUS_KEY = "status";
    private static final String JSON_MESSAGE_KEY = "message";

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    private String accountId = "72332423443245";
    private String returnUrl = "http://service.url/success-page/";

    @Before
    public void setupGatewayAccount() {
        app.getDatabaseTestHelper().addGatewayAccount(accountId, "test gateway");
    }

    @Test
    public void makeChargeAndRetrieveAmount() throws Exception {
        long expectedAmount = 2113l;
        String postBody = toJson(ImmutableMap.of(
                JSON_AMOUNT_KEY, expectedAmount,
                JSON_GATEWAY_ACC_KEY, accountId,
                JSON_RETURN_URL_KEY, returnUrl));
        ValidatableResponse response = postCreateChargeResponse(postBody)
                .statusCode(201)
                .body(JSON_CHARGE_KEY, is(notNullValue()))
                .body(JSON_AMOUNT_KEY, isNumber(expectedAmount))
                .body(JSON_RETURN_URL_KEY, is(returnUrl))
                .contentType(JSON);

        String chargeId = response.extract().path(JSON_CHARGE_KEY);
        String documentLocation = expectedChargeLocationFor(chargeId);

        response.header("Location", is(documentLocation));
        assertSelfLink(response, documentLocation);
        assertNextUrlLink(response, cardDetailsLocationFor(chargeId));

        ValidatableResponse getChargeResponse = getChargeResponseFor(chargeId)
                .statusCode(200)
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(chargeId))
                .body(JSON_AMOUNT_KEY, isNumber(expectedAmount))
                .body(JSON_STATUS_KEY, is("CREATED"))
                .body(JSON_RETURN_URL_KEY, is(returnUrl));

        assertSelfLink(getChargeResponse, documentLocation);
    }

    @Test
    public void shouldFilterChargeStatusToReturnInProgressIfInternalStatusIsAuthorised() throws Exception {
        String chargeId = ((Integer) RandomUtils.nextInt(99999999)).toString();
        app.getDatabaseTestHelper().addCharge(chargeId, accountId, 500, AUTHORISATION_SUCCESS, returnUrl);
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");
        
        getChargeResponseFor(chargeId)
                .statusCode(200)
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(chargeId))
                .body(JSON_STATUS_KEY, is("IN PROGRESS"));
    }

    @Test
    public void cannotMakeChargeForMissingGatewayAccount() throws Exception {
        String missingGatewayAccount = "1234123";
        String postBody = toJson(ImmutableMap.of(
                JSON_AMOUNT_KEY, 2113,
                JSON_GATEWAY_ACC_KEY, missingGatewayAccount,
                JSON_RETURN_URL_KEY, returnUrl));
        postCreateChargeResponse(postBody)
                .statusCode(400)
                .contentType(JSON)
                .header("Location", is(nullValue()))
                .body(JSON_CHARGE_KEY, is(nullValue()))
                .body(JSON_MESSAGE_KEY, is("Unknown gateway account: " + missingGatewayAccount));
    }

    @Test
    public void cannotMakeChargeForMissingFields() throws Exception {
        postCreateChargeResponse("{}")
                .statusCode(400)
                .contentType(JSON)
                .header("Location", is(nullValue()))
                .body(JSON_CHARGE_KEY, is(nullValue()))
                .body(JSON_MESSAGE_KEY, is("Field(s) missing: [amount, gateway_account_id, return_url]"));
    }

    @Test
    public void cannotGetCharge_WhenInvalidChargeId() throws Exception {
        String chargeId = "23235124";
        getChargeResponseFor(chargeId)
                .statusCode(404)
                .contentType(JSON)
                .body(JSON_MESSAGE_KEY, is(format("Charge with id [%s] not found.", chargeId)));
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
        String chargeTokenId = app.getDatabaseTestHelper().getChargeTokenId(chargeId);
        return "http://Frontend" + FRONTEND_CARD_DETAILS_URL + chargeId + "?chargeTokenId=" + chargeTokenId;
    }
}
