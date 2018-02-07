package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.response.ValidatableResponse;
import org.apache.commons.lang.math.RandomUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.model.api.ExternalChargeState;
import uk.gov.pay.connector.model.domain.Address;
import uk.gov.pay.connector.model.domain.AuthCardDetails;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.pay.connector.util.RestAssuredClient;

import javax.ws.rs.core.HttpHeaders;
import java.time.ZonedDateTime;
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
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.lang.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang.RandomStringUtils.randomAlphanumeric;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static uk.gov.pay.connector.matcher.ResponseContainsLinkMatcher.containsLink;
import static uk.gov.pay.connector.matcher.ZoneDateTimeAsStringWithinMatcher.isWithin;
import static uk.gov.pay.connector.model.domain.AuthCardDetails.anAuthCardDetails;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.NumberMatcher.isNumber;

public class ChargesFrontendResourceITest {
    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    private String accountId = "72332423443245";
    private String description = "Test description";
    private String returnUrl = "http://whatever.com";
    private String email = randomAlphabetic(242) + "@example.com";
    private String serviceName = "a cool service";
    private String analyticsId = "test-123";
    private String type = "test";
    private String paymentProvider = "sandbox";
    private long expectedAmount = 6234L;

    private RestAssuredClient connectorRestApi = new RestAssuredClient(app, accountId);

    @Before
    public void setupGatewayAccount() {
        DatabaseFixtures databaseFixtures = DatabaseFixtures.withDatabaseTestHelper(app.getDatabaseTestHelper());
        DatabaseFixtures.TestCardType mastercard = databaseFixtures.aMastercardDebitCardType().insert();
        DatabaseFixtures.TestCardType visa = databaseFixtures.aVisaCreditCardType().insert();
        app.getDatabaseTestHelper().addGatewayAccount(accountId, paymentProvider, description, analyticsId);
        app.getDatabaseTestHelper().addAcceptedCardType(Long.valueOf(accountId), mastercard.getId());
        app.getDatabaseTestHelper().addAcceptedCardType(Long.valueOf(accountId), visa.getId());
    }

    @Test
    public void getChargeShouldIncludeExpectedLinksAndGatewayAccount() throws Exception {

        String chargeId = postToCreateACharge(expectedAmount);
        String expectedLocation = "https://localhost:" + app.getLocalPort() + "/v1/frontend/charges/" + chargeId;

        validateGetCharge(expectedAmount, chargeId, CREATED)
                .body("links", hasSize(3))
                .body("links", containsLink("self", GET, expectedLocation))
                .body("links", containsLink("cardAuth", POST, expectedLocation + "/cards"))
                .body("links", containsLink("cardCapture", POST, expectedLocation + "/capture")).extract().response();
    }


    @Test
    public void shouldReturnInternalChargeStatusIfStatusIsAuthorised() throws Exception {
        String externalChargeId = RandomIdGenerator.newId();
        Long chargeId = 123456L;

        setupChargeAndPaymentRequest(chargeId, externalChargeId, AUTHORISATION_SUCCESS);

        validateGetCharge(expectedAmount, externalChargeId, AUTHORISATION_SUCCESS);
    }

    @Test
    public void shouldReturnEmptyCardBrandLabelIfStatusIsAuthorisedAndBrandUnknown() throws Exception {
        String externalChargeId = RandomIdGenerator.newId();
        Long chargeId = 123456L;

        setupChargeAndPaymentRequest(chargeId, externalChargeId, AUTHORISATION_SUCCESS);

        validateGetCharge(expectedAmount, externalChargeId, AUTHORISATION_SUCCESS);
    }

    @Test
    public void shouldIncludeAuth3dsDataInResponse() {
        String externalChargeId = RandomIdGenerator.newId();
        Long chargeId = 98765L;
        String issuerUrl = "https://issuer.example.com/3ds";
        String paRequest = "test-pa-request";

        Pair<Long, Long> ids = setupChargeAndPaymentRequest(chargeId, externalChargeId, AUTHORISATION_3DS_REQUIRED);
        app.getDatabaseTestHelper().updateCharge3dsDetails(chargeId, issuerUrl, paRequest);

        app.getDatabaseTestHelper().addCard3ds(ids.getRight(), ids.getLeft(), paRequest, issuerUrl);

        connectorRestApi
                .withChargeId(externalChargeId)
                .getFrontendCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("auth_3ds_data.paRequest", is(paRequest))
                .body("auth_3ds_data.issuerUrl", is(issuerUrl));
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
        app.getDatabaseTestHelper().addGatewayAccount(anotherAccountId, "worldpay");
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
        String email = randomAlphabetic(242) + "@example.com";

        String patchBody = createPatch("replace", "email", email);

        ValidatableResponse response = connectorRestApi
                .withChargeId(chargeId)
                .patchCharge(patchBody);

        response.statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("email", is(email));
    }

    @Test
    public void sanitize_patchValidEmailOnChargeWithReplaceOp_shouldReturnOk_withSanitizedData() {

        String chargeId = postToCreateACharge(expectedAmount);
        String email = "r-12-34-5  Ju&^6501-76@example.com";
        String sanitizedEmail = "r-**-**-*  Ju&^****-**@example.com";

        String patchBody = createPatch("replace", "email", email);

        ValidatableResponse response = connectorRestApi
                .withChargeId(chargeId)
                .patchCharge(patchBody);

        response.statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("email", is(sanitizedEmail));
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
                .put("return_url", returnUrl).build());

        //No longer check for email as it is not set when we create a PaymentRequest as this feature was not being used
        ValidatableResponse response = connectorRestApi
                .withAccountId(accountId)
                .postCreateCharge(postBody)
                .statusCode(Status.CREATED.getStatusCode())
                .body("charge_id", is(notNullValue()))
                .body("reference", is(reference))
                .body("description", is(description))
                .body("amount", isNumber(expectedAmount))
                .body("return_url", is(returnUrl))
                .body("created_date", is(notNullValue()))
                .contentType(JSON);

        return response.extract().path("charge_id");
    }

    private ValidatableResponse validateGetCharge(long expectedAmount, String chargeId, ChargeStatus chargeStatus) {
        ValidatableResponse response = connectorRestApi
                .withChargeId(chargeId)
                .getFrontendCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("charge_id", is(chargeId))
                .body("containsKey('reference')", is(false))
                .body("description", is(description))
                .body("amount", isNumber(expectedAmount))
                .body("status", is(chargeStatus.getValue()))
                .body("return_url", is(returnUrl))
                .body("created_date", is(notNullValue()))
                .body("created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3}Z"))
                .body("created_date", isWithin(10, SECONDS));
        validateGatewayAccount(response);
        validateCardDetails(response, chargeStatus);
        return response;
    }

    private void validateGatewayAccount(ValidatableResponse response) {
        response
                .body("containsKey('gateway_account')", is(true))
                .body("gateway_account.gateway_account_id", is(Long.valueOf(accountId)))
                .body("gateway_account.containsKey('credentials')", is(false))
                .body("gateway_account.service_name", is(serviceName))
                .body("gateway_account.payment_provider", is(paymentProvider))
                .body("gateway_account.type", is(type))
                .body("gateway_account.analytics_id", is(analyticsId))
                .body("gateway_account.card_types", is(notNullValue()))
                .body("gateway_account.card_types[0].id", is(notNullValue()))
                .body("gateway_account.card_types[0].label", is("MasterCard"))
                .body("gateway_account.card_types[0].type", is("DEBIT"))
                .body("gateway_account.card_types[0].brand", is("mastercard"))
                .body("gateway_account.card_types[1].id", is(notNullValue()))
                .body("gateway_account.card_types[1].label", is("Visa"))
                .body("gateway_account.card_types[1].type", is("CREDIT"))
                .body("gateway_account.card_types[1].brand", is("visa"));
    }

    private void validateCardDetails(ValidatableResponse response, ChargeStatus status) {
        if (status.equals(ChargeStatus.AUTHORISATION_SUCCESS)) {
            response
                    .body("card_details", is(notNullValue()))
                    .body("card_details.charge_id", is(nullValue()))
                    .body("card_details.last_digits_card_number", is("1234"))
                    .body("card_details.cardholder_name", is("Mr. McPayment"))
                    .body("card_details.expiry_date", is("03/18"))
                    .body("card_details.billing_address", is(notNullValue()))
                    .body("card_details.billing_address.line1", is("line1"))
                    .body("card_details.billing_address.line2", is(nullValue()))
                    .body("card_details.billing_address.city", is("city"))
                    .body("card_details.billing_address.postcode", is("postcode"))
                    .body("card_details.billing_address.country", is("country"))
                    .body("card_details.billing_address.county", is(nullValue()));
        } else {
            response.body("containsKey('card_details')", is(false));
        }
    }

    private static void setupLifeCycleEventsFor(DropwizardAppWithPostgresRule app, Long chargeId, List<ChargeStatus> statuses) {
        statuses.stream().forEach(
                st -> app.getDatabaseTestHelper().addEvent(chargeId, st.getValue())
        );
    }

    private static String createPatch(String op, String path, String value) {
        return toJson(ImmutableMap.of("op", op, "path", path, "value", value));

    }

    private Pair<Long, Long> setupChargeAndPaymentRequest(Long chargeId, String externalChargeId, ChargeStatus status) {
        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, expectedAmount, status, returnUrl, null, "ref", null, email);
        String cardBrand = "unknown";
        String lastDigitsCardNumber = "1234";
        String cardHolderName = "Mr. McPayment";
        String expiryDate = "03/18";
        String addressLine1 = "line1";
        String addressLine2 = null;
        String postcode = "postcode";
        String city = "city";
        String county = null;
        String country = "country";

        Address address = new Address();
        address.setLine1(addressLine1);
        address.setLine2(addressLine2);
        address.setPostcode(postcode);
        address.setCity(city);
        address.setCounty(county);
        address.setCountry(country);

        app.getDatabaseTestHelper().updateChargeCardDetails(chargeId, cardBrand, lastDigitsCardNumber, cardHolderName,
                expiryDate, addressLine1, addressLine2, postcode, city, county, country);

        long paymentRequestId = RandomUtils.nextLong();
        app.getDatabaseTestHelper().addPaymentRequest(paymentRequestId, expectedAmount, Long.valueOf(accountId), returnUrl,
                "Test description", "ref", ZonedDateTime.now(), externalChargeId);
        long chargeTransactionId = RandomUtils.nextLong();
        app.getDatabaseTestHelper().addChargeTransaction(chargeTransactionId, null, expectedAmount, status, paymentRequestId);
        AuthCardDetails cardDetails = anAuthCardDetails();
        cardDetails.setCardBrand(cardBrand);
        cardDetails.setCardNo(lastDigitsCardNumber);
        cardDetails.setCardHolder(cardHolderName);
        cardDetails.setEndDate(expiryDate);
        cardDetails.setAddress(address);

        long cardId = RandomUtils.nextLong();
        app.getDatabaseTestHelper().addCard(cardId, chargeTransactionId, cardDetails);

        return new ImmutablePair<>(chargeTransactionId, cardId);
    }
}
