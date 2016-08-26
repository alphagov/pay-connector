package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.response.ValidatableResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.model.api.ExternalChargeState;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.ConfirmationDetailsEntity;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.pay.connector.util.RestAssuredClient;

import javax.ws.rs.core.HttpHeaders;
import java.util.List;

import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.Arrays.asList;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status;
import static javax.ws.rs.core.Response.Status.*;
import static org.apache.commons.lang.RandomStringUtils.randomAlphanumeric;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static uk.gov.pay.connector.matcher.ResponseContainsLinkMatcher.containsLink;
import static uk.gov.pay.connector.matcher.ZoneDateTimeAsStringWithinMatcher.isWithin;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.NumberMatcher.isNumber;

public class ChargesFrontendResourceITest {
    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    private String accountId = "72332423443245";
    private String description = "Test description";
    private String returnUrl = "http://whatever.com";
    private String email = randomAlphanumeric(242) + "@example.com";

    private long expectedAmount = 6234L;

    private RestAssuredClient connectorRestApi = new RestAssuredClient(app, accountId);

    @Before
    public void setupGatewayAccount() {
        app.getDatabaseTestHelper().addGatewayAccount(accountId, "test gateway");
    }

    @Test
    public void getChargeShouldIncludeExpectedLinksButNotGatewayAccountId() throws Exception {

        String chargeId = postToCreateACharge(expectedAmount);
        String expectedLocation = "http://localhost:" + app.getLocalPort() + "/v1/frontend/charges/" + chargeId;

        validateGetCharge(expectedAmount, chargeId, CREATED)
                .body("links", hasSize(3))
                .body("links", containsLink("self", GET, expectedLocation))
                .body("links", containsLink("cardAuth", POST, expectedLocation + "/cards"))
                .body("links", containsLink("cardCapture", POST, expectedLocation + "/capture"));
    }


    @Test
    public void shouldReturnInternalChargeStatusAndConfirmationDetailsIfStatusIsAuthorised() throws Exception {
        String externalChargeId = RandomIdGenerator.newId();
        Long chargeId = 123456L;

        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, expectedAmount, AUTHORISATION_SUCCESS, returnUrl, null, "ref", null, email);
        validateGetCharge(expectedAmount, externalChargeId, AUTHORISATION_SUCCESS);

        app.getDatabaseTestHelper().addConfirmationDetails(chargeId, "1234", "Mr. McPayment",  "03/18", "line1", null, "postcode", "city", null, "country");
        validateConfirmationDetails(externalChargeId);
    }

    @Test
    public void shouldUpdateChargeStatusToEnteringCardDetails() {

        String chargeId = postToCreateACharge(expectedAmount);
        String putBody = toJson(ImmutableMap.of("new_status", ENTERING_CARD_DETAILS.getValue()));

        connectorRestApi
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

        connectorRestApi
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

        connectorRestApi
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
        connectorRestApi
                .withChargeId(chargeId)
                .getCharge()
                .statusCode(NOT_FOUND.getStatusCode())
                .contentType(JSON)
                .body("message", is(format("Charge with id [%s] not found.", chargeId)));
    }

    //TODO getTransactions test should sit in the ChargesAPIResourceTest and not in here as it uses end points defined in the APIResource
    @Test
    public void shouldReturnAllTransactionsForAGivenGatewayAccount() {

        Long chargeId1 = 10001L;
        String externalChargeId1 = "10001";
        Long chargeId2 = 10002L;
        String externalChargeId2 = "10002";

        int amount1 = 100;
        int amount2 = 500;
        String gatewayTransactionId1 = "transaction-id-1";

        app.getDatabaseTestHelper().addCharge(chargeId1, externalChargeId1, accountId, amount1, AUTHORISATION_SUCCESS, returnUrl, gatewayTransactionId1);
        app.getDatabaseTestHelper().addCharge(chargeId2, externalChargeId2, accountId, amount2, AUTHORISATION_REJECTED, returnUrl, null);

        String anotherAccountId = "5454545";
        Long chargeId3 = 5001L;
        app.getDatabaseTestHelper().addGatewayAccount(anotherAccountId, "another test gateway");
        app.getDatabaseTestHelper().addCharge(chargeId3, "charge5001", anotherAccountId, 200, AUTHORISATION_READY, returnUrl, "transaction-id-2");

        List<ChargeStatus> statuses = asList(CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_READY, AUTHORISATION_SUCCESS);
        setupLifeCycleEventsFor(app, chargeId1, statuses);
        setupLifeCycleEventsFor(app, chargeId2, statuses);
        setupLifeCycleEventsFor(app, chargeId3, statuses);

        ValidatableResponse response = connectorRestApi.withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON).getTransactions();

        response.statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results", hasSize(2));
        assertTransactionEntry(response, 0, externalChargeId2, null, amount2, ExternalChargeState.EXTERNAL_FAILED_REJECTED.getStatus());
        assertTransactionEntry(response, 1, externalChargeId1, gatewayTransactionId1, amount1, ExternalChargeState.EXTERNAL_SUBMITTED.getStatus());
    }

    @Test
    public void shouldReturnTransactionsOnDescendingOrderOfChargeId() {

        app.getDatabaseTestHelper().addCharge(101L, "charge101", accountId, 500, AUTHORISATION_SUCCESS, returnUrl, randomUUID().toString());
        app.getDatabaseTestHelper().addCharge(102L, "charge102", accountId, 300, AUTHORISATION_REJECTED, returnUrl, null);
        app.getDatabaseTestHelper().addCharge(103L, "charge103", accountId, 100, AUTHORISATION_READY, returnUrl, randomUUID().toString());

        List<ChargeStatus> statuses = asList(CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_READY, AUTHORISATION_SUCCESS, CAPTURE_SUBMITTED, CAPTURED);
        setupLifeCycleEventsFor(app, 101L, statuses);
        setupLifeCycleEventsFor(app, 102L, statuses);
        setupLifeCycleEventsFor(app, 103L, statuses);

        ValidatableResponse response = connectorRestApi.withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON).getTransactions();

        response.statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results", hasSize(3));

        response.body("results[" + 0 + "].charge_id", is("charge103"));
        response.body("results[" + 1 + "].charge_id", is("charge102"));
        response.body("results[" + 2 + "].charge_id", is("charge101"));

    }

    @Test
    public void shouldReturn404_IfNoAccountExistsForTheGivenAccountId() {
        String nonExistentAccountId = "123456789";
        ValidatableResponse response = connectorRestApi
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .withAccountId(nonExistentAccountId)
                .getTransactions();

        response.statusCode(NOT_FOUND.getStatusCode())
                .contentType(JSON)
                .body("message", is(format("account with id %s not found", nonExistentAccountId)));
    }

    @Test
    public void shouldReturn404IfGatewayAccountIsMissingWhenListingTransactions() {
        ValidatableResponse response = connectorRestApi
                .withAccountId("")
                .getTransactions();

        response.statusCode(NOT_FOUND.getStatusCode());
    }

    @Test
    public void shouldReturn404IfGatewayAccountIsNotANumberWhenListingTransactions() {
        String invalidAccRef = "XYZ";
        ValidatableResponse response = connectorRestApi
                .withAccountId(invalidAccRef)
                .getTransactions();

        response.statusCode(NOT_FOUND.getStatusCode())
                .contentType(JSON)
                .body("code", is(404))
                .body("message", is("HTTP 404 Not Found"));
    }

    @Test
    public void shouldReturnEmptyResult_IfNoTransactionsExistForAccount() {
        ValidatableResponse response = connectorRestApi
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactions();

        response.statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results", hasSize(0));
    }

    @Test
    public void patchValidEmailOnChargeWithReplaceOp_shouldReturnOk() {
        String chargeId = postToCreateACharge(expectedAmount);
        String email = randomAlphanumeric(242) + "@example.com";

        String patchBody = createPatch("replace", "email", email);

        ValidatableResponse response = connectorRestApi
                .withChargeId(chargeId)
                .patchCharge(patchBody);

        response.statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("email", is(email));
    }

    @Test
    public void patchValidEmailOnChargeWithAnyOpExceptReplace_shouldReturnBadRequest() {
        String chargeId = postToCreateACharge(expectedAmount);
        String patchBody = createPatch("delete", "email", "a@b.c");

        ValidatableResponse response = connectorRestApi
                .withChargeId(chargeId)
                .patchCharge(patchBody);

        response.statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", is("Bad patch parameters{op=delete, path=email, value=a@b.c}"));
    }


    @Test
    public void patchInvalidEmailOnCharge_shouldReturnBadRequest() {
        String chargeId = postToCreateACharge(expectedAmount);
        String patchBody = createPatch("replace", "email", "@ab.c");

        ValidatableResponse response = connectorRestApi
                .withChargeId(chargeId)
                .patchCharge(patchBody);

        response.statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", is("Invalid patch parameters{op=replace, path=email, value=@ab.c}"));
    }

    @Test
    public void patchTooLongEmailOnCharge_shouldReturnBadRequest() {
        String chargeId = postToCreateACharge(expectedAmount);
        String tooLongEmail = randomAlphanumeric(243) + "@example.com";
        String patchBody = createPatch("replace", "email", tooLongEmail);

        ValidatableResponse response = connectorRestApi
                .withChargeId(chargeId)
                .patchCharge(patchBody);

        response.statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", is(format("Invalid patch parameters{op=replace, path=email, value=%s}", tooLongEmail)));
    }

    @Test
    public void patchUnpatchableChargeField_shouldReturnBadRequest() {
        String chargeId = postToCreateACharge(expectedAmount);
        String patchBody = createPatch("replace", "amount", "1");

        ValidatableResponse response = connectorRestApi
                .withChargeId(chargeId)
                .patchCharge(patchBody);

        response.statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", is("Bad patch parameters{op=replace, path=amount, value=1}"));
    }


    private void assertTransactionEntry(ValidatableResponse response, int index, String externalChargeId, String gatewayTransactionId, int amount, String chargeStatus) {
        response.body("results[" + index + "].charge_id", is(externalChargeId))
                .body("results[" + index + "].gateway_transaction_id", is(gatewayTransactionId))
                .body("results[" + index + "].amount", is(amount))
                .body("results[" + index + "].state.status", is(chargeStatus));
    }

    private String postToCreateACharge(long expectedAmount) {
        String reference = "Test reference";
        String postBody = toJson(ImmutableMap.builder()
                .put("reference", reference)
                .put("description", description)
                .put("amount", expectedAmount)
                .put("gateway_account_id", accountId)
                .put("return_url", returnUrl)
                .put("email", email).build());

        ValidatableResponse response = connectorRestApi
                .withAccountId(accountId)
                .postCreateCharge(postBody)
                .statusCode(Status.CREATED.getStatusCode())
                .body("charge_id", is(notNullValue()))
                .body("reference", is(reference))
                .body("description", is(description))
                .body("amount", isNumber(expectedAmount))
                .body("return_url", is(returnUrl))
                .body("email", is(email))
                .body("created_date", is(notNullValue()))
                .contentType(JSON);

        return response.extract().path("charge_id");
    }

    private ValidatableResponse validateGetCharge(long expectedAmount, String chargeId, ChargeStatus chargeStatus) {

        return connectorRestApi
                .withChargeId(chargeId)
                .getFrontendCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("charge_id", is(chargeId))
                .body("containsKey('reference')", is(false))
                .body("description", is(description))
                .body("amount", isNumber(expectedAmount))
                .body("containsKey('gateway_account_id')", is(false))
                .body("status", is(chargeStatus.getValue()))
                .body("return_url", is(returnUrl))
                .body("email", is(email))
                .body("confirmation_details", is(nullValue()))
                .body("created_date", is(notNullValue()))
                .body("created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3}Z"))
                .body("created_date", isWithin(10, SECONDS));
    }
    private ValidatableResponse validateConfirmationDetails(String externalChargeId) {

        return connectorRestApi
                .withChargeId(externalChargeId)
                .getFrontendCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("confirmation_details", is(notNullValue()))
                .body("confirmation_details.charge_id", is(nullValue()))
                .body("confirmation_details.last_digits_card_number", is("1234"))
                .body("confirmation_details.cardholder_name", is("Mr. McPayment"))
                .body("confirmation_details.expiry_date", is("03/18"))
                .body("confirmation_details.billing_address", is(notNullValue()))
                .body("confirmation_details.billing_address.line1", is("line1"))
                .body("confirmation_details.billing_address.line2", is(nullValue()))
                .body("confirmation_details.billing_address.city", is("city"))
                .body("confirmation_details.billing_address.postcode", is("postcode"))
                .body("confirmation_details.billing_address.country", is("country"))
                .body("confirmation_details.billing_address.county", is(nullValue()));

    }
    private static void setupLifeCycleEventsFor(DropwizardAppWithPostgresRule app, Long chargeId, List<ChargeStatus> statuses) {
        statuses.stream().forEach(
                st -> app.getDatabaseTestHelper().addEvent(chargeId, st.getValue())
        );
    }

    private static String createPatch(String op, String path, String value) {
        return toJson(ImmutableMap.of("op", op, "path", path, "value", value));

    }
}
