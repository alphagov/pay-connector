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
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static java.time.ZonedDateTime.now;
import static java.util.Arrays.asList;
import static javax.ws.rs.core.Response.Status.*;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.exparity.hamcrest.date.ZonedDateTimeMatchers.within;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.*;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.EXT_IN_PROGRESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.resources.ApiPaths.CHARGE_API_PATH;
import static uk.gov.pay.connector.util.DateTimeUtils.toUTCZonedDateTime;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.LinksAssert.assertNextUrlLink;
import static uk.gov.pay.connector.util.LinksAssert.assertSelfLink;
import static uk.gov.pay.connector.util.NumberMatcher.isNumber;

public class ChargesApiResourceITest {
    private static final String FRONTEND_CARD_DETAILS_URL = "/charge/";

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
                .contentType(JSON);

        String chargeId = response.extract().path(JSON_CHARGE_KEY);
        String documentLocation = expectedChargeLocationFor(accountId, chargeId);

        response.header("Location", is(documentLocation));
        assertSelfLink(response, documentLocation);
        assertNextUrlLink(response, cardDetailsLocationFor(chargeId));

        ValidatableResponse getChargeResponse = getChargeApi
                .withAccountId(accountId)
                .withChargeId(chargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(chargeId))
                .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                .body(JSON_REFERENCE_KEY, is(expectedReference))
                .body(JSON_DESCRIPTION_KEY, is(expectedDescription))
                .body(JSON_STATUS_KEY, is(ChargeStatus.CREATED.getValue()))
                .body(JSON_RETURN_URL_KEY, is(returnUrl));

        assertSelfLink(getChargeResponse, documentLocation);
    }

    @Test
    public void shouldFilterChargeStatusToReturnInProgressIfInternalStatusIsAuthorised() throws Exception {
        String chargeId = ((Integer) RandomUtils.nextInt(99999999)).toString();
        app.getDatabaseTestHelper().addCharge(chargeId, accountId, AMOUNT, AUTHORISATION_SUCCESS, returnUrl, null);
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");

        getChargeApi
                .withAccountId(accountId)
                .withChargeId(chargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(chargeId))
                .body(JSON_STATUS_KEY, is(EXT_IN_PROGRESS.getValue()));
    }

    @Test
    public void shouldGetChargeTransactionsForCSVAcceptHeader() throws Exception {
        String chargeId = ((Integer) RandomUtils.nextInt(99999999)).toString();
        ChargeStatus chargeStatus = AUTHORISATION_SUCCESS;
        app.getDatabaseTestHelper().addCharge(chargeId, accountId, AMOUNT, chargeStatus, returnUrl, null);
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");
        app.getDatabaseTestHelper().addEvent(Long.valueOf(chargeId), chargeStatus.getValue());

        getChargeApi
                .withAccountId(accountId)
                .withHeader(HttpHeaders.ACCEPT, CSV_CONTENT_TYPE)
                .getTransactions()
                .statusCode(OK.getStatusCode())
                .contentType(CSV_CONTENT_TYPE)
                .body(containsString(
                        "Service Payment Reference,Amount,Status,Gateway Transaction ID,GOV.UK Pay ID,Date Created\n" +
                                "Test reference,62.34,IN PROGRESS,," + chargeId));
    }

    @Test
    public void shouldGetChargeTransactionsForJSONAcceptHeader() throws Exception {
        String chargeId = ((Integer) RandomUtils.nextInt(99999999)).toString();
        ChargeStatus chargeStatus = AUTHORISATION_SUCCESS;
        app.getDatabaseTestHelper().addCharge(chargeId, accountId, AMOUNT, chargeStatus, returnUrl, null);
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");
        app.getDatabaseTestHelper().addEvent(Long.valueOf(chargeId), chargeStatus.getValue());

        getChargeApi
                .withAccountId(accountId)
                .withHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
                .getTransactions()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.charge_id", hasItem(chargeId))
                .body("results.status", hasItem(EXT_IN_PROGRESS.getValue()));
    }

    @Test
    public void shouldNotGetRepeatedExternalChargeEvents() throws Exception {
        String chargeId = ((Integer) RandomUtils.nextInt(99999999)).toString();
        ChargeStatus chargeStatus = AUTHORISATION_SUCCESS;
        app.getDatabaseTestHelper().addCharge(chargeId, accountId, AMOUNT, chargeStatus, returnUrl, null);
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");

        List<ChargeStatus> statuses = asList(CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_SUBMITTED, ChargeStatus.SYSTEM_CANCELLED, ChargeStatus.ENTERING_CARD_DETAILS);
        setupLifeCycleEventsFor(app, Long.valueOf(chargeId), statuses);

        getChargeApi
                .withAccountId(accountId)
                .withHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
                .getEvents(new Long(chargeId))
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("charge_id", is(Integer.parseInt(chargeId)))
                .body("events.status", hasSize(4))
                .body("events.status[0]", is(EXT_CREATED.getValue()))
                .body("events.status[1]", is(EXT_IN_PROGRESS.getValue()))
                .body("events.status[2]", is(EXT_SYSTEM_CANCELLED.getValue()))
                .body("events.status[3]", is(EXT_IN_PROGRESS.getValue()));

    }

    @Test
    public void shouldFilterTransactionsBasedOnFromAndToDates() throws Exception {
        addCharge(CREATED, "ref-1", now());
        addCharge(AUTHORISATION_SUBMITTED, "ref-2", now());
        addCharge(CAPTURED, "ref-3", now().minusDays(2));

        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("from_date", DateTimeUtils.toUTCDateString(now().minusDays(1)))
                .withQueryParam("to_date", DateTimeUtils.toUTCDateString(now().plusDays(1)))
                .withHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
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
                assertThat(createdDate, is(within(1, ChronoUnit.DAYS, now())))
        );
    }

    @Test
    public void shouldError400_IfFromAndToDatesAreNotInCorrectFormatDuringFilterJson() throws Exception {
        getChargeApi
                .withAccountId(accountId)
                .withQueryParam("from_date", "invalid-date-string")
                .withQueryParam("to_date", "Another invalid date")
                .withHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
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

    private List<ZonedDateTime> datesFrom(List<String> createdDateStrings) {
        List<ZonedDateTime> dateTimes = newArrayList();
        createdDateStrings.stream().forEach(aDateString -> dateTimes.add(toUTCZonedDateTime(aDateString).get()));
        return dateTimes;
    }

    private String addCharge(ChargeStatus status, String reference, ZonedDateTime fromDate) {
        String chargeId = ((Integer) RandomUtils.nextInt(99999999)).toString();
        ChargeStatus chargeStatus = status != null ? status : AUTHORISATION_SUCCESS;
        app.getDatabaseTestHelper().addCharge(chargeId, accountId, AMOUNT, chargeStatus, returnUrl, null, reference, fromDate);
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");
        app.getDatabaseTestHelper().addEvent(Long.valueOf(chargeId), chargeStatus.getValue());
        return chargeId;
    }

    private List<String> collect(List<Map<String, Object>> results, String field) {
        return results.stream().map(result -> result.get(field).toString()).collect(Collectors.toList());
    }

    private String expectedChargeLocationFor(String accountId, String chargeId) {
        return "http://localhost:" + app.getLocalPort() + CHARGE_API_PATH
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId);
    }

    private String cardDetailsLocationFor(String chargeId) {
        String chargeTokenId = app.getDatabaseTestHelper().getChargeTokenId(chargeId);
        return "http://Frontend" + FRONTEND_CARD_DETAILS_URL + chargeId + "?chargeTokenId=" + chargeTokenId;
    }

    private static void setupLifeCycleEventsFor(DropwizardAppWithPostgresRule app, Long chargeId, List<ChargeStatus> statuses) {
        statuses.stream().forEach(
                st -> app.getDatabaseTestHelper().addEvent(chargeId, st.getValue())
        );
    }

}
