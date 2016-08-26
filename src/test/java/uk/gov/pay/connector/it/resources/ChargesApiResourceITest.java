package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.response.ValidatableResponse;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.DateTimeUtils;
import uk.gov.pay.connector.util.RestAssuredClient;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response.Status;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static java.time.ZonedDateTime.now;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.*;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.exparity.hamcrest.date.ZonedDateTimeMatchers.within;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static uk.gov.pay.connector.matcher.ResponseContainsLinkMatcher.containsLink;
import static uk.gov.pay.connector.matcher.ZoneDateTimeAsStringWithinMatcher.isWithin;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.resources.ApiPaths.CHARGES_API_PATH;
import static uk.gov.pay.connector.resources.ApiPaths.CHARGE_API_PATH;
import static uk.gov.pay.connector.util.DateTimeUtils.toUTCZonedDateTime;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.NumberMatcher.isNumber;

public class ChargesApiResourceITest {

    private static final String FRONTEND_CARD_DETAILS_URL = "/secure";
    private static final String JSON_AMOUNT_KEY = "amount";
    private static final String JSON_REFERENCE_KEY = "reference";
    private static final String JSON_DESCRIPTION_KEY = "description";
    private static final String JSON_GATEWAY_ACC_KEY = "gateway_account_id";
    private static final String JSON_RETURN_URL_KEY = "return_url";
    private static final String JSON_CHARGE_KEY = "charge_id";
    private static final String JSON_STATE_KEY = "state.status";
    private static final String JSON_MESSAGE_KEY = "message";
    private static final String JSON_EMAIL_KEY = "email";
    private static final String JSON_PROVIDER_KEY = "payment_provider";
    private static final String PROVIDER_NAME = "sandbox";
    private static final long AMOUNT = 6234L;

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    private String accountId = "72332423443245";
    private String returnUrl = "http://service.url/success-page/";
    private String email = randomAlphanumeric(242) + "@example.com";

    private RestAssuredClient createChargeApi = new RestAssuredClient(app, accountId);
    private RestAssuredClient getChargeApi = new RestAssuredClient(app, accountId);

    @Before
    public void setupGatewayAccount() {
        app.getDatabaseTestHelper().addGatewayAccount(accountId, PROVIDER_NAME);
    }

    @Test
    public void makeChargeAndRetrieveAmount() throws Exception {

        String expectedReference = "Test reference";
        String expectedDescription = "Test description";
        String postBody = toJson(ImmutableMap.builder()
                .put(JSON_AMOUNT_KEY, AMOUNT)
                .put(JSON_REFERENCE_KEY, expectedReference)
                .put(JSON_DESCRIPTION_KEY, expectedDescription)
                .put(JSON_GATEWAY_ACC_KEY, accountId)
                .put(JSON_RETURN_URL_KEY, returnUrl)
                .put(JSON_EMAIL_KEY, email).build());

        ValidatableResponse response = createChargeApi
                .postCreateCharge(postBody)
                .statusCode(Status.CREATED.getStatusCode())
                .body(JSON_CHARGE_KEY, is(notNullValue()))
                .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                .body(JSON_REFERENCE_KEY, is(expectedReference))
                .body(JSON_DESCRIPTION_KEY, is(expectedDescription))
                .body(JSON_PROVIDER_KEY, is(PROVIDER_NAME))
                .body(JSON_RETURN_URL_KEY, is(returnUrl))
                .body(JSON_EMAIL_KEY, is(email))
                .body("containsKey('confirmation_details')", is(false))
                .body("containsKey('gateway_account')", is(false))
                .body("refund_summary.amount_submitted", is(0))
                .body("refund_summary.amount_available", isNumber(AMOUNT))
                .body("refund_summary.status", is("unavailable"))
                .body("created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3}Z"))
                .body("created_date", isWithin(10, SECONDS))
                .contentType(JSON);

        String externalChargeId = response.extract().path(JSON_CHARGE_KEY);
        String documentLocation = expectedChargeLocationFor(accountId, externalChargeId);
        String chargeTokenId = app.getDatabaseTestHelper().getChargeTokenByExternalChargeId(externalChargeId);

        String hrefNextUrl = "http://Frontend" + FRONTEND_CARD_DETAILS_URL + "/" + chargeTokenId;
        String hrefNextUrlPost = "http://Frontend" + FRONTEND_CARD_DETAILS_URL;

        response.header("Location", is(documentLocation))
                .body("links", hasSize(4))
                .body("links", containsLink("self", "GET", documentLocation))
                .body("links", containsLink("refunds", "GET", documentLocation + "/refunds"))
                .body("links", containsLink("next_url", "GET", hrefNextUrl))
                .body("links", containsLink("next_url_post", "POST", hrefNextUrlPost, "application/x-www-form-urlencoded", new HashMap<String, Object>() {{
                    put("chargeTokenId", chargeTokenId);
                }}));

        ValidatableResponse getChargeResponse = getChargeApi
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(externalChargeId))
                .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                .body(JSON_REFERENCE_KEY, is(expectedReference))
                .body(JSON_DESCRIPTION_KEY, is(expectedDescription))
                .body(JSON_STATE_KEY, is(CREATED.toExternal().getStatus()))
                .body(JSON_RETURN_URL_KEY, is(returnUrl))
                .body(JSON_EMAIL_KEY, is(email))
                .body("confirmation_details", is(nullValue()))
                .body("refund_summary.amount_submitted", is(0))
                .body("refund_summary.amount_available", isNumber(AMOUNT))
                .body("refund_summary.status", is("unavailable"));


        // Reload the charge token which as it should have changed
        String newChargeTokenId = app.getDatabaseTestHelper().getChargeTokenByExternalChargeId(externalChargeId);

        String newHrefNextUrl = "http://Frontend" + FRONTEND_CARD_DETAILS_URL + "/" + newChargeTokenId;

        getChargeResponse
                .body("links", hasSize(4))
                .body("links", containsLink("self", "GET", documentLocation))
                .body("links", containsLink("refunds", "GET", documentLocation + "/refunds"))
                .body("links", containsLink("next_url", "GET", newHrefNextUrl))
                .body("links", containsLink("next_url_post", "POST", hrefNextUrlPost, "application/x-www-form-urlencoded", new HashMap<String, Object>() {{
                    put("chargeTokenId", newChargeTokenId);
                }}));

    }

    @Test
    public void makeChargeNoEmailField_shouldReturnOK() throws Exception {
        String expectedReference = "Test reference";
        String expectedDescription = "Test description";
        String postBody = toJson(ImmutableMap.builder()
                .put(JSON_AMOUNT_KEY, AMOUNT)
                .put(JSON_REFERENCE_KEY, expectedReference)
                .put(JSON_DESCRIPTION_KEY, expectedDescription)
                .put(JSON_GATEWAY_ACC_KEY, accountId)
                .put(JSON_EMAIL_KEY, email)
                .put(JSON_RETURN_URL_KEY, returnUrl).build());


        createChargeApi
                .postCreateCharge(postBody)
                .statusCode(Status.CREATED.getStatusCode())
                .body(JSON_CHARGE_KEY, is(notNullValue()))
                .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                .body(JSON_REFERENCE_KEY, is(expectedReference))
                .body(JSON_DESCRIPTION_KEY, is(expectedDescription))
                .body(JSON_PROVIDER_KEY, is(PROVIDER_NAME))
                .body(JSON_RETURN_URL_KEY, is(returnUrl))
                .body("confirmation_details", is(nullValue()))
                .body("created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3}Z"))
                .body("created_date", isWithin(10, SECONDS))
                .contentType(JSON);

    }

    @Test
    public void makeChargeInvalidEmail_shouldReturnBadRequest() throws Exception {
        String expectedReference = "Test reference";
        String expectedDescription = "Test description";
        String postBody = toJson(ImmutableMap.builder()
                .put(JSON_AMOUNT_KEY, AMOUNT)
                .put(JSON_REFERENCE_KEY, expectedReference)
                .put(JSON_DESCRIPTION_KEY, expectedDescription)
                .put(JSON_GATEWAY_ACC_KEY, accountId)
                .put(JSON_EMAIL_KEY, "invalidemail...")
                .put(JSON_RETURN_URL_KEY, returnUrl).build());

        createChargeApi
                .postCreateCharge(postBody)
                .contentType(JSON)
                .statusCode(BAD_REQUEST.getStatusCode())
                .header("Location", is(nullValue()))
                .body(JSON_CHARGE_KEY, is(nullValue()))
                .body(JSON_MESSAGE_KEY, is("Field(s) are invalid: [email]"));
    }

    @Test
    public void shouldReturn404WhenCreatingChargeAccountIdIsNonNumeric() {

        String postBody = toJson(ImmutableMap.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, "Test reference",
                JSON_DESCRIPTION_KEY, "Test description",
                JSON_GATEWAY_ACC_KEY, accountId,
                JSON_RETURN_URL_KEY, returnUrl));

        createChargeApi
                .withAccountId("invalidAccountId")
                .postCreateCharge(postBody)
                .contentType(JSON)
                .statusCode(NOT_FOUND.getStatusCode())
                .body("code", is(404))
                .body("message", is("HTTP 404 Not Found"));
    }

    @Test
    public void shouldReturn404OnGetChargeWhenAccountIdIsNonNumeric() {
        getChargeApi
                .withAccountId("wrongAccount")
                .withChargeId("123")
                .withHeader(HttpHeaders.ACCEPT, JSON.getAcceptHeader())
                .getCharge()
                .contentType(JSON)
                .statusCode(NOT_FOUND.getStatusCode())
                .body("code", is(404))
                .body("message", is("HTTP 404 Not Found"));
    }

    @Test
    public void shouldReturn404OnGetTransactionsWhenAccountIdIsNonNumeric() {
        getChargeApi
                .withAccountId("invalidAccountId")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactions()
                .contentType(JSON)
                .statusCode(NOT_FOUND.getStatusCode())
                .body("code", is(404))
                .body("message", is("HTTP 404 Not Found"));
    }

    @Test
    public void shouldReturn400WhenAmountIsOverMaxAmount() {

        String expectedReference = "Test reference";
        String expectedDescription = "Test description";
        String postBody = toJson(ImmutableMap.builder()
                .put(JSON_AMOUNT_KEY, 10000001)
                .put(JSON_REFERENCE_KEY, expectedReference)
                .put(JSON_DESCRIPTION_KEY, expectedDescription)
                .put(JSON_GATEWAY_ACC_KEY, accountId)
                .put(JSON_RETURN_URL_KEY, returnUrl)
                .put(JSON_EMAIL_KEY, email).build());

       createChargeApi
                .postCreateCharge(postBody)
                .statusCode(Status.BAD_REQUEST.getStatusCode());

    }

    @Test
    public void shouldReturn400WhenAmountIsLessThanMinAmount() {

        String expectedReference = "Test reference";
        String expectedDescription = "Test description";
        String postBody = toJson(ImmutableMap.builder()
                .put(JSON_AMOUNT_KEY, 0)
                .put(JSON_REFERENCE_KEY, expectedReference)
                .put(JSON_DESCRIPTION_KEY, expectedDescription)
                .put(JSON_GATEWAY_ACC_KEY, accountId)
                .put(JSON_RETURN_URL_KEY, returnUrl)
                .put(JSON_EMAIL_KEY, email).build());

        createChargeApi
                .postCreateCharge(postBody)
                .statusCode(Status.BAD_REQUEST.getStatusCode());

    }

    @Test
    public void shouldFilterChargeStatusToReturnInProgressIfInternalStatusIsAuthorised() throws Exception {

        long chargeId = RandomUtils.nextInt();
        String externalChargeId = "charge1";

        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, AMOUNT, AUTHORISATION_SUCCESS, returnUrl, null);
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");

        getChargeApi
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(externalChargeId))
                .body(JSON_STATE_KEY, is(EXTERNAL_SUBMITTED.getStatus()));
    }

    @Test
    public void shouldGetChargeTransactionsForJSONAcceptHeader() throws Exception {

        long chargeId = RandomUtils.nextInt();
        String externalChargeId = "charge3";

        ChargeStatus chargeStatus = AUTHORISATION_SUCCESS;
        ZonedDateTime createdDate = ZonedDateTime.of(2016, 1, 26, 13, 45, 32, 123, ZoneId.of("UTC"));
        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, AMOUNT, chargeStatus, returnUrl, null, "My reference", createdDate);
        app.getDatabaseTestHelper().addConfirmationDetails(chargeId, "1234", "Mr. McPayment",  "03/18", "line1", null, "postcode", "city", null, "country");
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");
        app.getDatabaseTestHelper().addEvent(chargeId, chargeStatus.getValue());
        getChargeApi
                .withAccountId(accountId)
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactions()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results[0].charge_id", is(externalChargeId))
                .body("results[0].state.status", is(EXTERNAL_SUBMITTED.getStatus()))
                .body("results[0].amount", is(6234))
                .body("results[0].confirmation_details", nullValue())
                .body("results[0].reference", is("My reference"))
                .body("results[0].return_url", is(returnUrl))
                .body("results[0].description", is("Test description"))
                .body("results[0].created_date", is("2016-01-26T13:45:32Z"))
                .body("results[0].payment_provider", is(PROVIDER_NAME));
    }

    @Test
    public void shouldFilterTransactionsBasedOnFromAndToDates() throws Exception {

        addChargeAndConfirmationDetails(CREATED, "ref-1", now());
        addChargeAndConfirmationDetails(AUTHORISATION_READY, "ref-2", now());
        addChargeAndConfirmationDetails(CAPTURED, "ref-3", now().minusDays(2));

        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("from_date", DateTimeUtils.toUTCDateString(now().minusDays(1)))
                .withQueryParam("to_date", DateTimeUtils.toUTCDateString(now().plusDays(1)))
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactions()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2))
                .body("results[0].confirmation_details", is(nullValue()))
                .body("results[0].refund_summary.amount_submitted", is(0))
                .body("results[0].refund_summary.amount_available", isNumber(AMOUNT))
                .body("results[0].refund_summary.status", is("unavailable"))
                .body("results[1].refund_summary.amount_submitted", is(0))
                .body("results[1].refund_summary.amount_available", isNumber(AMOUNT))
                .body("results[1].refund_summary.status", is("unavailable"));

        List<Map<String, Object>> results = response.extract().body().jsonPath().getList("results");
        List<String> references = collect(results, "reference");
        assertThat(references, containsInAnyOrder("ref-1", "ref-2"));
        assertThat(references, not(contains("ref-3")));

        // collect statuses from states
        List<Object> statuses = results
                .stream()
                .map(result -> ((Map<Object, Object>) result.get("state")).get("status"))
                .collect(Collectors.toList());

        assertThat(statuses, containsInAnyOrder("created", "started"));
        assertThat(statuses, not(contains("confirmed")));

        List<String> createdDateStrings = collect(results, "created_date");
        datesFrom(createdDateStrings).forEach(createdDate ->
                assertThat(createdDate, is(within(1, DAYS, now())))
        );
        List<String> emails = collect(results, "email");
        assertThat(emails, contains(email, email));
    }

    @Test
    public void shouldFilterTransactionsByEmail() throws Exception {

        addChargeAndConfirmationDetails(CREATED, "ref-1", now());
        addChargeAndConfirmationDetails(AUTHORISATION_READY, "ref-2", now());
        addChargeAndConfirmationDetails(CAPTURED, "ref-3", now().minusDays(2));

        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("email", "@example.com")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactions()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(3))
                .body("results[0].email", endsWith("example.com"));
    }

    @Test
    public void shouldShowTotalCountResultsAndHalLinksForCharges() throws Exception {
        addChargeAndConfirmationDetails(CREATED, "ref-1", now());
        addChargeAndConfirmationDetails(AUTHORISATION_READY, "ref-2", now().minusDays(1));
        addChargeAndConfirmationDetails(CAPTURED, "ref-3", now().minusDays(2));
        addChargeAndConfirmationDetails(CAPTURED, "ref-4", now().minusDays(3));
        addChargeAndConfirmationDetails(CAPTURED, "ref-5", now().minusDays(4));

        assertResultsWhenPageAndDisplaySizeNotSet();
        assertResultsAndJustSelfLinkWhenJustOneResult();
        assertResultsAndNoPrevLinkWhenOnFirstPage();
        assertResultsAndAllLinksWhenOnMiddlePage();
        assertResultsAndNoNextLinksWhenOnLastPage();
        assert404WhenRequestingInvalidPage();
        assertBadRequestForNegativePageDisplaySize();
    }

    @Test
    public void shouldShowValidationsForDateAndPageDisplaySize() throws Exception {
        ImmutableList<String> expectedList = ImmutableList.of(
                "query param 'from_date' not in correct format",
                "query param 'to_date' not in correct format",
                "query param 'display_size' should be a non zero positive integer",
                "query param 'page' should be a non zero positive integer");

        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("to_date", "-1")
                .withQueryParam("from_date", "-1")
                .withQueryParam("page", "-1")
                .withQueryParam("display_size", "-2")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactions()
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", is(expectedList));
    }

    @Test
    public void shouldGetAllTransactionsForDefault_page_1_size_100_inCreationDateOrder() throws Exception {
        String id_1 = addChargeAndConfirmationDetails(CREATED, "ref-1", now());
        String id_2 = addChargeAndConfirmationDetails(AUTHORISATION_READY, "ref-2", now().plusHours(1));
        String id_3 = addChargeAndConfirmationDetails(CREATED, "ref-3", now().plusHours(2));
        String id_4 = addChargeAndConfirmationDetails(CREATED, "ref-4", now().plusHours(3));
        String id_5 = addChargeAndConfirmationDetails(CREATED, "ref-5", now().plusHours(4));

        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactions()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(5));

        List<Map<String, Object>> results = response.extract().body().jsonPath().getList("results");
        List<String> references = collect(results, "charge_id");
        assertThat(references, is(ImmutableList.of(id_5, id_4, id_3, id_2, id_1)));
    }

    @Test
    public void shouldGetTransactionsForPageAndSizeParams_inCreationDateOrder() throws Exception {
        String id_1 = addChargeAndConfirmationDetails(CREATED, "ref-1", now());
        String id_2 = addChargeAndConfirmationDetails(CREATED, "ref-2", now().plusHours(1));
        String id_3 = addChargeAndConfirmationDetails(CREATED, "ref-3", now().plusHours(2));
        String id_4 = addChargeAndConfirmationDetails(CREATED, "ref-4", now().plusHours(3));
        String id_5 = addChargeAndConfirmationDetails(CREATED, "ref-5", now().plusHours(4));

        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("display_size", "2")
                .withQueryParam("page", "1")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactions()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2));

        List<Map<String, Object>> results = response.extract().body().jsonPath().getList("results");
        List<String> charge_ids = collect(results, "charge_id");
        assertThat(charge_ids, is(ImmutableList.of(id_5, id_4)));

        response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("display_size", "2")
                .withQueryParam("page", "2")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactions()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2));

        results = response.extract().body().jsonPath().getList("results");
        charge_ids = collect(results, "charge_id");
        assertThat(charge_ids, is(ImmutableList.of(id_3, id_2)));

        response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("display_size", "2")
                .withQueryParam("page", "3")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactions()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(1));

        results = response.extract().body().jsonPath().getList("results");
        charge_ids = collect(results, "charge_id");
        assertThat(charge_ids, is(ImmutableList.of(id_1)));
    }

    @Test
    public void cannotMakeChargeForMissingGatewayAccount() throws Exception {
        String missingGatewayAccount = "1234123";
        String postBody = toJson(ImmutableMap.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, "Test reference",
                JSON_DESCRIPTION_KEY, "Test description",
                JSON_EMAIL_KEY, email,
                JSON_RETURN_URL_KEY, returnUrl));

        createChargeApi
                .withAccountId(missingGatewayAccount)
                .postCreateCharge(postBody)
                .statusCode(NOT_FOUND.getStatusCode())
                .contentType(JSON)
                .header("Location", is(nullValue()))
                .body(JSON_CHARGE_KEY, is(nullValue()))
                .body(JSON_MESSAGE_KEY, is("Unknown gateway account: " + missingGatewayAccount));
    }

    @Test
    public void cannotMakeChargeForInvalidSizeOfFields() throws Exception {
        String postBody = toJson(ImmutableMap.builder()
                        .put(JSON_AMOUNT_KEY, AMOUNT)
                        .put(JSON_REFERENCE_KEY, randomAlphabetic(256))
                        .put(JSON_DESCRIPTION_KEY, randomAlphanumeric(256))
                        .put(JSON_EMAIL_KEY, randomAlphanumeric(255))
                        .put(JSON_GATEWAY_ACC_KEY, accountId)
                        .put(JSON_RETURN_URL_KEY, returnUrl).build());

        createChargeApi.postCreateCharge(postBody)
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .header("Location", is(nullValue()))
                .body(JSON_CHARGE_KEY, is(nullValue()))
                .body(JSON_MESSAGE_KEY, is("Field(s) are too big: [description, reference, email]"));
    }

    @Test
    public void cannotMakeChargeForMissingFields() throws Exception {
        createChargeApi.postCreateCharge("{}")
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .header("Location", is(nullValue()))
                .body(JSON_CHARGE_KEY, is(nullValue()))
                .body(JSON_MESSAGE_KEY, is("Field(s) missing: [amount, description, reference, return_url]"));
    }

    @Test
    public void cannotGetCharge_WhenInvalidChargeId() throws Exception {
        String chargeId = "23235124";
        getChargeApi
                .withAccountId(accountId)
                .withChargeId(chargeId)
                .getCharge()
                .statusCode(NOT_FOUND.getStatusCode())
                .contentType(JSON)
                .body(JSON_MESSAGE_KEY, is(format("Charge with id [%s] not found.", chargeId)));
    }

    @Test
    public void shouldGetSuccessAndFailedResponseForExpiryChargeTask() {
        //create charge
        String extChargeId = addChargeAndConfirmationDetails(CREATED, "ref", ZonedDateTime.now().minusHours(1));

        // run expiry task
        getChargeApi
                .postChargeExpiryTask()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("expiry-success", is(1))
                .body("expiry-failed", is(0));

        // get the charge back and assert its status is expired
        getChargeApi
                .withAccountId(accountId)
                .withChargeId(extChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(extChargeId))
                .body(JSON_STATE_KEY, is(EXPIRED.toExternal().getStatus()));

    }

    private void assert404WhenRequestingInvalidPage() {
        // when 5 charges are there, page is 10, display-size is 2
        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("reference", "ref")
                .withQueryParam("page", "10")
                .withQueryParam("display_size", "2")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactions()
                .statusCode(NOT_FOUND.getStatusCode())
                .body("message", is("the requested page not found"));
    }

    private void assertBadRequestForNegativePageDisplaySize() {
        ImmutableList<String> expectedList = ImmutableList.of(
                "query param 'display_size' should be a non zero positive integer",
                "query param 'page' should be a non zero positive integer");

        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("reference", "ref")
                .withQueryParam("page", "-1")
                .withQueryParam("display_size", "-2")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactions()
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", is(expectedList));
    }

    private void assertResultsAndJustSelfLinkWhenJustOneResult() {
        // when 5 charges are there, page is 1, display-size is 2
        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("reference", "ref-1")
                .withQueryParam("page", "1")
                .withQueryParam("display_size", "2")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactions()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                // then no prev and next link
                .body("results.size()", is(1))
                .body("total", is(1))
                .body("count", is(1))
                .body("_links.next_page", isEmptyOrNullString())
                .body("_links.prev_page", isEmptyOrNullString())
                .body("_links.first_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref-1&page=1&display_size=2")))
                .body("_links.last_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref-1&page=1&display_size=2")))
                .body("_links.self.href", is(expectedChargesLocationFor(accountId, "?reference=ref-1&page=1&display_size=2")));

        List<Map<String, Object>> results = response.extract().body().jsonPath().getList("results");
        List<String> references = collect(results, "reference");
        assertThat(references, containsInAnyOrder("ref-1"));
        assertThat(references, not(contains("ref-1", "ref-3", "ref-4", "ref-5")));
    }

    private void assertResultsWhenPageAndDisplaySizeNotSet() {
        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("reference", "ref-1")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactions()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                // then no prev and next link
                .body("results.size()", is(1))
                .body("total", is(1))
                .body("count", is(1))
                .body("_links.next_page", isEmptyOrNullString())
                .body("_links.prev_page", isEmptyOrNullString())
                .body("_links.first_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref-1&page=1&display_size=500")))
                .body("_links.last_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref-1&page=1&display_size=500")))
                .body("_links.self.href", is(expectedChargesLocationFor(accountId, "?reference=ref-1&page=1&display_size=500")));

        List<Map<String, Object>> results = response.extract().body().jsonPath().getList("results");
        List<String> references = collect(results, "reference");
        assertThat(references, containsInAnyOrder("ref-1"));
        assertThat(references, not(contains("ref-1", "ref-3", "ref-4", "ref-5")));
    }

    private void assertResultsAndNoPrevLinkWhenOnFirstPage() {
        // when 5 charges are there, page is 1, display-size is 2
        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("reference", "ref")
                .withQueryParam("page", "1")
                .withQueryParam("display_size", "2")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactions()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                // then no prev link
                .body("results.size()", is(2))
                .body("total", is(5))
                .body("count", is(2))
                .body("_links.next_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref&page=2&display_size=2")))
                .body("_links.prev_page", isEmptyOrNullString())
                .body("_links.first_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref&page=1&display_size=2")))
                .body("_links.last_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref&page=3&display_size=2")))
                .body("_links.self.href", is(expectedChargesLocationFor(accountId, "?reference=ref&page=1&display_size=2")));

        List<Map<String, Object>> results = response.extract().body().jsonPath().getList("results");
        List<String> references = collect(results, "reference");
        assertThat(references, containsInAnyOrder("ref-1", "ref-2"));
        assertThat(references, not(contains("ref-3", "ref-4", "ref-5")));
    }

    private void assertResultsAndAllLinksWhenOnMiddlePage() {
        // when 5 charges are there, page is 2, display-size is 2
        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("reference", "ref")
                .withQueryParam("page", "2")
                .withQueryParam("display_size", "2")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactions()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                // then all links present
                .body("results.size()", is(2))
                .body("total", is(5))
                .body("count", is(2))
                .body("_links.next_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref&page=3&display_size=2")))
                .body("_links.prev_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref&page=1&display_size=2")))
                .body("_links.first_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref&page=1&display_size=2")))
                .body("_links.last_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref&page=3&display_size=2")))
                .body("_links.self.href", is(expectedChargesLocationFor(accountId, "?reference=ref&page=2&display_size=2")));

        List<Map<String, Object>> results = response.extract().body().jsonPath().getList("results");
        List<String> references = collect(results, "reference");
        assertThat(references, containsInAnyOrder("ref-3", "ref-4"));
        assertThat(references, not(contains("ref-1", "ref-2", "ref-5")));
    }

    private void assertResultsAndNoNextLinksWhenOnLastPage() {
        // when 5 charges are there, page is 3, display-size is 2
        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("reference", "ref")
                .withQueryParam("page", "3")
                .withQueryParam("display_size", "2")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactions()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                // then no next link
                .body("results.size()", is(1))
                .body("total", is(5))
                .body("count", is(1))
                .body("_links.next_page", isEmptyOrNullString())
                .body("_links.prev_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref&page=2&display_size=2")))
                .body("_links.first_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref&page=1&display_size=2")))
                .body("_links.last_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref&page=3&display_size=2")))
                .body("_links.self.href", is(expectedChargesLocationFor(accountId, "?reference=ref&page=3&display_size=2")));

        List<Map<String, Object>> results = response.extract().body().jsonPath().getList("results");
        List<String> references = collect(results, "reference");
        assertThat(references, containsInAnyOrder("ref-5"));
        assertThat(references, not(contains("ref-1", "ref-2", "ref-3", "ref-4")));
    }

    private List<ZonedDateTime> datesFrom(List<String> createdDateStrings) {
        List<ZonedDateTime> dateTimes = newArrayList();
        createdDateStrings.stream().forEach(aDateString -> dateTimes.add(toUTCZonedDateTime(aDateString).get()));
        return dateTimes;
    }

    private String addChargeAndConfirmationDetails(ChargeStatus status, String reference, ZonedDateTime fromDate) {
        long chargeId = RandomUtils.nextInt();
        String externalChargeId = "charge" + chargeId;
        ChargeStatus chargeStatus = status != null ? status : AUTHORISATION_SUCCESS;
        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, AMOUNT, chargeStatus, returnUrl, null, reference, fromDate, email);
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");
        app.getDatabaseTestHelper().addEvent(chargeId, chargeStatus.getValue());
        app.getDatabaseTestHelper().addConfirmationDetails(chargeId, "1234", "Mr. McPayment",  "03/18", "line1", null, "postcode", "city", null, "country");
        return externalChargeId;
    }

    private List<String> collect(List<Map<String, Object>> results, String field) {
        return results.stream().map(result -> result.get(field).toString()).collect(Collectors.toList());
    }

    private String expectedChargeLocationFor(String accountId, String chargeId) {
        return "http://localhost:" + app.getLocalPort() + CHARGE_API_PATH
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId);
    }

    private String expectedChargesLocationFor(String accountId, String queryParams) {
        return "http://localhost:" + app.getLocalPort()
                + CHARGES_API_PATH.replace("{accountId}", accountId)
                + queryParams;
    }
}
