package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.response.ValidatableResponse;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.RestAssuredClient;

import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.core.Response.Status;
import static javax.ws.rs.core.Response.Status.*;
import static org.hamcrest.Matchers.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.resources.ApiPaths.*;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.LinksAssert.assertLink;
import static uk.gov.pay.connector.util.LinksAssert.assertSelfLink;
import static uk.gov.pay.connector.util.NumberMatcher.isNumber;

public class ChargesFrontendResourceITest {
    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    private String accountId = "72332423443245";
    private String description = "Test description";
    private String reference = "Test reference";
    private String returnUrl = "http://whatever.com";
    private long expectedAmount = 6234L;

    //TODO: This requires refactoring to move to the new REST endpoints convention
    private RestAssuredClient restOldApiCall = new RestAssuredClient(app, accountId, OLD_CHARGES_API_PATH);
    private RestAssuredClient restOldFrontendCall = new RestAssuredClient(app, accountId, OLD_CHARGES_FRONTEND_PATH);
    private RestAssuredClient restFrontendCall = new RestAssuredClient(app, accountId, OLD_GET_CHARGE_FRONTEND_PATH);

    @Before
    public void setupGatewayAccount() {
        app.getDatabaseTestHelper().addGatewayAccount(accountId, "test gateway");
    }

    @Test
    public void getChargeShouldIncludeCardAuthAndCardCaptureLinkButNotGatewayAccountId() throws Exception {
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
        app.getDatabaseTestHelper().addCharge(chargeId, accountId, expectedAmount, AUTHORISATION_SUCCESS, returnUrl, null);

        validateGetCharge(expectedAmount, chargeId, AUTHORISATION_SUCCESS);
    }

    @Test
    public void shouldUpdateChargeStatusToEnteringCardDetails() {
        String chargeId = postToCreateACharge(expectedAmount);
        String putBody = toJson(ImmutableMap.of("new_status", ENTERING_CARD_DETAILS.getValue()));

        restFrontendCall
                .withAccountId(accountId)
                .withChargeId(chargeId)
                .putChargeStatus(putBody)
                .statusCode(NO_CONTENT.getStatusCode())
                .body(isEmptyOrNullString());

        validateGetCharge(expectedAmount, chargeId, ENTERING_CARD_DETAILS);
    }

    @Test
    public void shouldBeBadRequestForUpdateStatusWithEmptyBody() {
        String chargeId = postToCreateACharge(expectedAmount);
        String putBody = "";

        restFrontendCall
                .withChargeId(chargeId)
                .putChargeStatus(putBody)
                .statusCode(BAD_REQUEST.getStatusCode())
                .body(is("{\"message\":\"Field(s) missing: [new_status]\"}"));

        //charge status should remain CREATED
        validateGetCharge(expectedAmount, chargeId, CREATED);
    }

    @Test
    public void shouldBeBadRequestForUpdateStatusForUnrecognisedStatus() {
        String chargeId = postToCreateACharge(expectedAmount);
        String putBody = toJson(ImmutableMap.of("new_status", "junk"));

        restFrontendCall
                .withAccountId(accountId)
                .withChargeId(chargeId)
                .putChargeStatus(putBody)
                .statusCode(BAD_REQUEST.getStatusCode())
                .body(is("{\"message\":\"charge status not recognized: junk\"}"));

        //charge status should remain CREATED
        validateGetCharge(expectedAmount, chargeId, CREATED);
    }

    @Test
    public void cannotGetCharge_WhenInvalidChargeId() throws Exception {
        String chargeId = "23235124";
        restFrontendCall
                .withChargeId(chargeId)
                .getCharge()
                .statusCode(NOT_FOUND.getStatusCode())
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

        ValidatableResponse response = restOldFrontendCall
                .getTransactions();

        response.statusCode(OK.getStatusCode())
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

        ValidatableResponse response = restOldFrontendCall
                .getTransactions();

        response.statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results", hasSize(3));

        response.body("results[" + 0 + "].charge_id", is("103"));
        response.body("results[" + 1 + "].charge_id", is("102"));
        response.body("results[" + 2 + "].charge_id", is("101"));

    }

    @Test
    public void shouldReturn404_IfNoAccountExistsForTheGivenAccountId() {
        String nonExistentAccountId = "123456789";
        ValidatableResponse response = restOldFrontendCall
                .withAccountId(nonExistentAccountId)
                .getTransactions();

        response.statusCode(NOT_FOUND.getStatusCode())
                .contentType(JSON)
                .body("message", is(format("account with id %s not found", nonExistentAccountId)));
    }

    @Test
    public void shouldReturn400IfGatewayAccountIsMissingWhenListingTransactions() {
        ValidatableResponse response = restOldFrontendCall
                .withAccountId("")
                .getTransactions();

        response.statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", is("missing gateway account reference"));
    }

    @Test
    public void shouldReturn400IfGatewayAccountIsNotANumberWhenListingTransactions() {
        String invalidAccRef = "XYZ";
        ValidatableResponse response = restOldFrontendCall
                .withAccountId(invalidAccRef)
                .getTransactions();

        response.statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", is(format("invalid gateway account reference %s", invalidAccRef)));
    }

    @Test
    public void shouldReturnEmptyResult_IfNoTransactionsExistForAccount() {
        ValidatableResponse response = restOldFrontendCall
                .getTransactions();

        response.statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results", hasSize(0));
    }

    private void assertTransactionEntry(ValidatableResponse response, int index, String chargeId, String gatewayTransactionId, int amount, String chargeStatus) {
        response.body("results[" + index + "].charge_id", is(chargeId))
                .body("results[" + index + "].gateway_transaction_id", is(gatewayTransactionId))
                .body("results[" + index + "].amount", is(amount))
                .body("results[" + index + "].status", is(chargeStatus));
    }

    private String postToCreateACharge(long expectedAmount) {
        String postBody = toJson(ImmutableMap.of(
                "reference", reference,
                "description", description,
                "amount", expectedAmount,
                "gateway_account_id", accountId,
                "return_url", returnUrl));

        ValidatableResponse response = restOldApiCall
                .withAccountId(accountId)
                .postCreateCharge(postBody)
                .statusCode(Status.CREATED.getStatusCode())
                .body("charge_id", is(notNullValue()))
                .body("reference", is(reference))
                .body("description", is(description))
                .body("amount", isNumber(expectedAmount))
                .body("return_url", is(returnUrl))
                .contentType(JSON);

        return response.extract().path("charge_id");
    }

    private ValidatableResponse validateGetCharge(long expectedAmount, String chargeId, ChargeStatus chargeStatus) {
        return restFrontendCall
                .withChargeId(chargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("charge_id", is(chargeId))
                .body("containsKey('reference')", is(false))
                .body("description", is(description))
                .body("amount", isNumber(expectedAmount))
                .body("containsKey('gateway_account_id')", is(false))
                .body("status", is(chargeStatus.getValue()))
                .body("return_url", is(returnUrl));
    }

    private String expectedChargeUrl(String chargeId, String path) {
        return "http://localhost:" + app.getLocalPort() + OLD_GET_CHARGE_FRONTEND_PATH.replace("{chargeId}", chargeId) + path;
    }
}