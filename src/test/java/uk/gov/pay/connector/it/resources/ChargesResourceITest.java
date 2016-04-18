package uk.gov.pay.connector.it.resources;

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
import static java.lang.Long.valueOf;
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
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static uk.gov.pay.connector.matcher.ResponseContainsLinkMatcher.containsLink;
import static uk.gov.pay.connector.matcher.ZoneDateTimeAsStringWithinMatcher.isWithin;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.EXT_IN_PROGRESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.resources.ApiPaths.CHARGE_API_PATH;
import static uk.gov.pay.connector.util.DateTimeUtils.toUTCZonedDateTime;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.NumberMatcher.isNumber;

public class ChargesResourceITest {

    private static final String FRONTEND_CARD_DETAILS_URL = "/secure";
    private static final String JSON_AMOUNT_KEY = "amount";
    private static final String JSON_REFERENCE_KEY = "reference";
    private static final String JSON_DESCRIPTION_KEY = "description";
    private static final String JSON_GATEWAY_ACC_KEY = "gateway_account_id";
    private static final String JSON_RETURN_URL_KEY = "return_url";
    private static final String JSON_CHARGE_KEY = "charge_id";
    private static final String JSON_STATUS_KEY = "status";
    private static final String JSON_MESSAGE_KEY = "message";
    private static final String JSON_PROVIDER_KEY = "payment_provider";
    private static final String CSV_CONTENT_TYPE = "text/csv";
    private static final String PROVIDER_NAME = "test_gateway";
    private static final long AMOUNT = 6234L;

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    private String accountId = "72332423443245";
    private String returnUrl = "http://service.url/success-page/";

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
        String postBody = toJson(ImmutableMap.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, expectedReference,
                JSON_DESCRIPTION_KEY, expectedDescription,
                JSON_GATEWAY_ACC_KEY, accountId,
                JSON_RETURN_URL_KEY, returnUrl));

        ValidatableResponse response = createChargeApi
                .postCreateCharge(postBody)
                .statusCode(Status.CREATED.getStatusCode())
                .body(JSON_CHARGE_KEY, is(notNullValue()))
                .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                .body(JSON_REFERENCE_KEY, is(expectedReference))
                .body(JSON_DESCRIPTION_KEY, is(expectedDescription))
                .body(JSON_PROVIDER_KEY, is(PROVIDER_NAME))
                .body(JSON_RETURN_URL_KEY, is(returnUrl))
                .body("created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3}Z"))
                .body("created_date", isWithin(10, SECONDS))
                .contentType(JSON);

        String externalChargeId = response.extract().path(JSON_CHARGE_KEY);
        String documentLocation = expectedChargeLocationFor(accountId, externalChargeId);
        String chargeTokenId = app.getDatabaseTestHelper().getChargeTokenByExternalChargeId(externalChargeId);

        String hrefNextUrl = "http://Frontend" + FRONTEND_CARD_DETAILS_URL + "/" + chargeTokenId;
        String hrefNextUrlPost = "http://Frontend" + FRONTEND_CARD_DETAILS_URL;

        response.header("Location", is(documentLocation))
                .body("links", hasSize(3))
                .body("links", containsLink("self", "GET", documentLocation))
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
                .body(JSON_STATUS_KEY, is(CREATED.getValue()))
                .body(JSON_RETURN_URL_KEY, is(returnUrl));

        // Reload the charge token which as it should have changed
        String newChargeTokenId = app.getDatabaseTestHelper().getChargeTokenByExternalChargeId(externalChargeId);

        String newHrefNextUrl = "http://Frontend" + FRONTEND_CARD_DETAILS_URL + "/" + newChargeTokenId;

        getChargeResponse.body("links", hasSize(3))
                .body("links", hasSize(3))
                .body("links", containsLink("self", "GET", documentLocation))
                .body("links", containsLink("next_url", "GET", newHrefNextUrl))
                .body("links", containsLink("next_url_post", "POST", hrefNextUrlPost, "application/x-www-form-urlencoded", new HashMap<String, Object>() {{
                    put("chargeTokenId", newChargeTokenId);
                }}));
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
    public void shouldReturn404OnGetTransactionsAsCSVWhenAccountIdIsNonNumeric() {
        getChargeApi
                .withAccountId("invalidAccountId")
                .withHeader(HttpHeaders.ACCEPT, CSV_CONTENT_TYPE)
                .getTransactions()
                .contentType(JSON)
                .statusCode(NOT_FOUND.getStatusCode())
                .body("code", is(404))
                .body("message", is("HTTP 404 Not Found"));
    }

    @Test
    public void shouldReturn404OnGetTransactionsAsJsonWhenAccountIdIsNonNumeric() {
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
                .body(JSON_STATUS_KEY, is(EXT_IN_PROGRESS.getValue()));
    }

    @Test
    public void shouldGetChargeTransactionsForCSVAcceptHeader() throws Exception {

        long chargeId = RandomUtils.nextInt();
        String externalChargeId = "charge4";

        ChargeStatus chargeStatus = AUTHORISATION_SUCCESS;
        ZonedDateTime createdDate = ZonedDateTime.of(2016, 1, 25, 13, 45, 32, 123, ZoneId.of("UTC"));
        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, AMOUNT, chargeStatus, returnUrl, null, "My reference", createdDate);
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");
        app.getDatabaseTestHelper().addEvent(valueOf(chargeId), chargeStatus.getValue());

        getChargeApi
                .withAccountId(accountId)
                .withHeader(HttpHeaders.ACCEPT, CSV_CONTENT_TYPE)
                .getTransactions()
                .statusCode(OK.getStatusCode())
                .contentType(CSV_CONTENT_TYPE)
                .body(is("Service Payment Reference,Amount,Status,Gateway Transaction ID,GOV.UK Pay ID,Date Created\n" +
                        "My reference,62.34,IN PROGRESS,," + externalChargeId + ",2016-01-25T13:45:32Z\n"));
    }

    @Test
    public void shouldGetChargeTransactionsForJSONAcceptHeader() throws Exception {

        long chargeId = RandomUtils.nextInt();
        String externalChargeId = "charge3";

        ChargeStatus chargeStatus = AUTHORISATION_SUCCESS;
        ZonedDateTime createdDate = ZonedDateTime.of(2016, 1, 26, 13, 45, 32, 123, ZoneId.of("UTC"));
        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, AMOUNT, chargeStatus, returnUrl, null, "My reference", createdDate);
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");
        app.getDatabaseTestHelper().addEvent(chargeId, chargeStatus.getValue());

        getChargeApi
                .withAccountId(accountId)
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactions()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.charge_id", hasItem(externalChargeId))
                .body("results.status", hasItem(EXT_IN_PROGRESS.getValue()))
                .body("results.amount", hasItem(6234))
                .body("results.reference", hasItem("My reference"))
                .body("results.return_url", hasItem(returnUrl))
                .body("results.description", hasItem("Test description"))
                .body("results.created_date", hasItem("2016-01-26T13:45:32Z"));
    }

    @Test
    public void shouldFilterTransactionsBasedOnFromAndToDates() throws Exception {
        addCharge(CREATED, "ref-1", now());
        addCharge(AUTHORISATION_READY, "ref-2", now());
        addCharge(CAPTURED, "ref-3", now().minusDays(2));

        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("from_date", DateTimeUtils.toUTCDateString(now().minusDays(1)))
                .withQueryParam("to_date", DateTimeUtils.toUTCDateString(now().plusDays(1)))
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactions()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2));

        List<Map<String, Object>> results = response.extract().body().jsonPath().getList("results");
        List<String> references = collect(results, "reference");
        assertThat(references, containsInAnyOrder("ref-1", "ref-2"));
        assertThat(references, not(contains("ref-3")));

        List<String> statuses = collect(results, "status");
        assertThat(statuses, containsInAnyOrder("CREATED", "IN PROGRESS"));
        assertThat(statuses, not(contains("SUCCEEDED")));

        List<String> createdDateStrings = collect(results, "created_date");
        datesFrom(createdDateStrings).forEach(createdDate ->
                assertThat(createdDate, is(within(1, DAYS, now())))
        );
    }

    @Test
    public void shouldError400_IfFromAndToDatesAreNotInCorrectFormatDuringFilterJson() throws Exception {
        getChargeApi
                .withAccountId(accountId)
                .withQueryParam("from_date", "invalid-date-string")
                .withQueryParam("to_date", "Another invalid date")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactions()
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", is("query parameters [from_date, to_date] not in correct format"));
    }

    @Test
    public void shouldError400_IfFromAndToDatesAreNotInCorrectFormatDuringFilterCsv() throws Exception {
        getChargeApi
                .withAccountId(accountId)
                .withQueryParam("from_date", "invalid-date-string")
                .withQueryParam("to_date", "Another invalid date")
                .withHeader(HttpHeaders.ACCEPT, CSV_CONTENT_TYPE)
                .getTransactions()
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType(CSV_CONTENT_TYPE)
                .body(containsString("query parameters [from_date, to_date] not in correct format"));
    }

    @Test
    public void cannotMakeChargeForMissingGatewayAccount() throws Exception {
        String missingGatewayAccount = "1234123";
        String postBody = toJson(ImmutableMap.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, "Test reference",
                JSON_DESCRIPTION_KEY, "Test description",
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
        String postBody = toJson(ImmutableMap.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, randomAlphabetic(256),
                JSON_DESCRIPTION_KEY, randomAlphanumeric(256),
                JSON_GATEWAY_ACC_KEY, accountId,
                JSON_RETURN_URL_KEY, returnUrl));

        createChargeApi.postCreateCharge(postBody)
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .header("Location", is(nullValue()))
                .body(JSON_CHARGE_KEY, is(nullValue()))
                .body(JSON_MESSAGE_KEY, is("Field(s) are too big: [description, reference]"));
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
        String extChargeId = addCharge(CREATED, "ref", ZonedDateTime.now().minusHours(1));

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
                .body(JSON_STATUS_KEY, is(EXPIRED.getValue()));

    }

    private List<ZonedDateTime> datesFrom(List<String> createdDateStrings) {
        List<ZonedDateTime> dateTimes = newArrayList();
        createdDateStrings.stream().forEach(aDateString -> dateTimes.add(toUTCZonedDateTime(aDateString).get()));
        return dateTimes;
    }

    private String addCharge(ChargeStatus status, String reference, ZonedDateTime fromDate) {
        long chargeId = RandomUtils.nextInt();
        String externalChargeId = "charge" + chargeId;
        ChargeStatus chargeStatus = status != null ? status : AUTHORISATION_SUCCESS;
        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, AMOUNT, chargeStatus, returnUrl, null, reference, fromDate);
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");
        app.getDatabaseTestHelper().addEvent(chargeId, chargeStatus.getValue());
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

}
