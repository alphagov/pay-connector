package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.response.ValidatableResponse;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.core.Response.Status;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.Matchers.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
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
        String chargeId = postToCreateACharge(expectedAmount);
        ValidatableResponse getChargeResponse = validateGetCharge(expectedAmount, chargeId, CREATED);

        String documentLocation = expectedChargeUrl(chargeId, "");
        assertSelfLink(getChargeResponse, documentLocation);

        String cardAuthUrl = expectedChargeUrl(chargeId, "/cards");
        assertLink(getChargeResponse, "cardAuth", POST, cardAuthUrl);

        String cardCaptureUrl = expectedChargeUrl(chargeId, "/capture");
        assertLink(getChargeResponse, "cardCapture", POST, cardCaptureUrl);
    }

    @Test
    public void shouldReturnInternalChargeStatusIfInternalStatusIsAuthorised() throws Exception {
        String chargeId = ((Integer) RandomUtils.nextInt(99999999)).toString();
        app.getDatabaseTestHelper().addCharge(chargeId, accountId, 500, AUTHORISATION_SUCCESS, returnUrl, null);

        validateGetCharge(500, chargeId, AUTHORISATION_SUCCESS);
    }

    @Test
    public void shouldUpdateChargeStatusToEnteringCardDetails() {
        long expectedAmount = 2113l;
        String chargeId = postToCreateACharge(expectedAmount);
        putChargeStatus(chargeId)
                .statusCode(NO_CONTENT.getStatusCode())
                .body(isEmptyOrNullString());

        validateGetCharge(expectedAmount, chargeId, ENTERING_CARD_DETAILS);
    }

    @Test
    public void cannotGetCharge_WhenInvalidChargeId() throws Exception {
        String chargeId = "23235124";
        getChargeResponseFor(chargeId)
                .statusCode(404)
                .contentType(JSON)
                .body("message", is(format("Charge with id [%s] not found.", chargeId)));
    }

    @Test
    public void shouldReturnAllTransactionsForAGivenGatewayAccount() {
        String chargeId1 = "10001";
        String chargeId2 = "10002";
        int amount1 = 100;
        int amount2 = 500;
        String gatewayTransactionId1 = "transaction-id-1";
        app.getDatabaseTestHelper().addCharge(chargeId1, accountId, amount1, AUTHORISATION_SUCCESS, returnUrl, gatewayTransactionId1);
        app.getDatabaseTestHelper().addCharge(chargeId2, accountId, amount2, AUTHORISATION_REJECTED, returnUrl, null);

        String anotherAccountId = "5454545";
        app.getDatabaseTestHelper().addGatewayAccount(anotherAccountId, "another test gateway");
        app.getDatabaseTestHelper().addCharge("5001", anotherAccountId, 200, AUTHORISATION_SUBMITTED, returnUrl, "transaction-id-2");

        ValidatableResponse response = listTransactionsFor(accountId);

        response.statusCode(200)
                .contentType(JSON)
                .body("results", hasSize(2));
        assertTransactionEntry(response, 0, chargeId2, null, amount2, AUTHORISATION_REJECTED.getValue());
        assertTransactionEntry(response, 1, chargeId1, gatewayTransactionId1, amount1, AUTHORISATION_SUCCESS.getValue());
    }

    @Test
    public void shouldReturnTransactionsOnDescendingOrderOfChargeId() {

        app.getDatabaseTestHelper().addCharge("101", accountId, 500, AUTHORISATION_SUCCESS, returnUrl, randomUUID().toString());
        app.getDatabaseTestHelper().addCharge("102", accountId, 300, AUTHORISATION_REJECTED, returnUrl, null);
        app.getDatabaseTestHelper().addCharge("103", accountId, 100, AUTHORISATION_SUBMITTED, returnUrl, randomUUID().toString());

        ValidatableResponse response = listTransactionsFor(accountId);

        response.statusCode(200)
                .contentType(JSON)
                .body("results", hasSize(3));

        response.body("results[" + 0 + "].charge_id", is("103"));
        response.body("results[" + 1 + "].charge_id", is("102"));
        response.body("results[" + 2 + "].charge_id", is("101"));

    }

    @Test
    public void shouldReturn404_IfNoAccountExistsForTheGivenAccountId() {

        String nonExistentAccountId = "123456789";
        ValidatableResponse response = listTransactionsFor(nonExistentAccountId);

        response.statusCode(404)
                .contentType(JSON)
                .body("message", is(format("account with id %s not found", nonExistentAccountId)));

    }

    @Test
    public void shouldReturn400IfGatewayAccountIsMissingWhenListingTransactions() {
        ValidatableResponse response = listTransactionsFor("");

        response.statusCode(400)
                .contentType(JSON)
                .body("message", is("missing gateway account reference"));

    }

    @Test
    public void shouldReturn400IfGatewayAccountIsNotANumberWhenListingTransactions() {
        String invalidAccRef = "XYZ";
        ValidatableResponse response = listTransactionsFor(invalidAccRef);

        response.statusCode(400)
                .contentType(JSON)
                .body("message", is(format("invalid gateway account reference %s", invalidAccRef)));

    }

    @Test
    public void shouldReturnEmptyResult_IfNoTransactionsExistForAccount() {
        ValidatableResponse response = listTransactionsFor(accountId);

        response.statusCode(200)
                .contentType(JSON)
                .body("results", hasSize(0));
    }

    private ValidatableResponse listTransactionsFor(String accountId) {
        return given().port(app.getLocalPort())
                .get(CHARGES_FRONTEND_PATH + "?gatewayAccountId=" + accountId)
                .then();
    }

    private void assertTransactionEntry(ValidatableResponse response, int index, String chargeId, String gatewayTransactionId, int amount, String chargeStatus) {
        response.body("results[" + index + "].charge_id", is(chargeId))
                .body("results[" + index + "].gateway_transaction_id", is(gatewayTransactionId))
                .body("results[" + index + "].amount", is(amount))
                .body("results[" + index + "].status", is(chargeStatus));
    }

    private String postToCreateACharge(long expectedAmount) {
        String postBody = toJson(ImmutableMap.of(
                "amount", expectedAmount,
                "gateway_account_id", accountId,
                "return_url", returnUrl));

        ValidatableResponse response = postCreateChargeResponse(postBody)
                .statusCode(Status.CREATED.getStatusCode())
                .body("charge_id", is(notNullValue()))
                .body("amount", isNumber(expectedAmount))
                .body("return_url", is(returnUrl))
                .contentType(JSON);

        return response.extract().path("charge_id");
    }

    private ValidatableResponse validateGetCharge(long expectedAmount, String chargeId, ChargeStatus chargeStatus) {
        return getChargeResponseFor(chargeId)
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("charge_id", is(chargeId))
                .body("amount", isNumber(expectedAmount))
                .body("containsKey('gateway_account_id')", is(false))
                .body("status", is(chargeStatus.getValue()))
                .body("return_url", is(returnUrl));
    }

    private ValidatableResponse getChargeResponseFor(String chargeId) {
        return given()
                .port(app.getLocalPort())
                .get(CHARGES_FRONTEND_PATH + chargeId)
                .then();
    }

    private ValidatableResponse postCreateChargeResponse(String postBody) {
        return given()
                .port(app.getLocalPort())
                .contentType(JSON)
                .body(postBody)
                .post(CHARGES_API_PATH)
                .then();
    }

    private ValidatableResponse putChargeStatus(String chargeId) {
        return given()
                .port(app.getLocalPort())
                .contentType(JSON)
                .put(CHARGES_FRONTEND_PATH + chargeId + "/status/" + ENTERING_CARD_DETAILS.getValue())
                .then();
    }

    private String expectedChargeUrl(String chargeId, String path) {
        return "http://localhost:" + app.getLocalPort() + CHARGES_FRONTEND_PATH + chargeId + path;
    }
}