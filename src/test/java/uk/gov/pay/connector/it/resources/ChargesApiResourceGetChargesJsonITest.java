package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableList;
import com.jayway.restassured.response.Header;
import com.jayway.restassured.response.ValidatableResponse;
import org.junit.Test;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.model.ServicePaymentReference;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.util.DateTimeUtils;
import uk.gov.pay.connector.util.RestAssuredClient;

import javax.ws.rs.core.HttpHeaders;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static java.time.ZonedDateTime.now;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Arrays.asList;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.lang.math.RandomUtils.nextInt;
import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.exparity.hamcrest.date.ZonedDateTimeMatchers.within;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.util.DateTimeUtils.toUTCZonedDateTime;

public class ChargesApiResourceGetChargesJsonITest extends ChargingITestBase {
    private static final String PROVIDER_NAME = "sandbox";
    private static final String RETURN_URL = "http://service.url/success-page/";
    private static final String EMAIL = randomAlphabetic(242) + "@example.com";
    private String returnUrl = "http://service.url/success-page/";
    private String email = randomAlphabetic(242) + "@example.com";

    private RestAssuredClient getChargeApi = new RestAssuredClient(app, accountId);

    public ChargesApiResourceGetChargesJsonITest() {
        super(PROVIDER_NAME);
    }

    @Test
    public void shouldReturn404OnGetTransactionsWhenAccountIdIsNonNumeric() {
        getChargeApi
                .withAccountId("invalidAccountId")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactionsAPI()
                .contentType(JSON)
                .statusCode(NOT_FOUND.getStatusCode())
                .body("code", is(404))
                .body("message", is("HTTP 404 Not Found"));
    }

    @Test
    public void shouldGetChargeTransactionsForJSONAcceptHeader() throws Exception {
        long chargeId = nextInt();
        String externalChargeId = "charge3";

        ChargeStatus chargeStatus = AUTHORISATION_SUCCESS;
        ZonedDateTime createdDate = ZonedDateTime.of(2016, 1, 26, 13, 45, 32, 123, ZoneId.of("UTC"));
        UUID card = UUID.randomUUID();
        app.getDatabaseTestHelper().addCardType(card, "label", "CREDIT", "brand", false);
        app.getDatabaseTestHelper().addAcceptedCardType(Long.valueOf(accountId), card);
        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, AMOUNT, chargeStatus, returnUrl, null,
                ServicePaymentReference.of("My reference"), createdDate, SupportedLanguage.WELSH);
        app.getDatabaseTestHelper().updateChargeCardDetails(chargeId, "VISA", "1234", "Mr. McPayment", "03/18", "line1", null, "postcode", "city", null, "country");
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");
        app.getDatabaseTestHelper().addEvent(chargeId, chargeStatus.getValue());

        String description = "Test description";
        app.getDatabaseTestHelper().addPaymentRequest(chargeId, AMOUNT, Long.valueOf(accountId), returnUrl, description,
                ServicePaymentReference.of("My reference"), createdDate, externalChargeId);
        app.getDatabaseTestHelper().addChargeTransaction(chargeId, null, Long.valueOf(accountId), AMOUNT, chargeStatus, chargeId, createdDate, email);
        app.getDatabaseTestHelper().addCard(chargeId, "VISA", chargeId);

        getChargeApi
                .withAccountId(accountId)
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactionsAPI()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results[0].charge_id", is(externalChargeId))
                .body("results[0].state.status", is(EXTERNAL_SUBMITTED.getStatus()))
                .body("results[0].amount", is(6234))
                .body("results[0].card_details", notNullValue())
                .body("results[0].gateway_account", nullValue())
                .body("results[0].reference", is("My reference"))
                .body("results[0].description", is(description))
                .body("results[0].created_date", is("2016-01-26T13:45:32Z"))
                .body("results[0].language", is(SupportedLanguage.WELSH.toString()));
    }

    @Test
    public void shouldGetChargeLegacyTransactions() throws Exception {

        long chargeId = nextInt();
        String externalChargeId = "charge3";

        ChargeStatus chargeStatus = AUTHORISATION_SUCCESS;
        ZonedDateTime createdDate = ZonedDateTime.of(2016, 1, 26, 13, 45, 32, 123, ZoneId.of("UTC"));
        UUID card = UUID.randomUUID();
        app.getDatabaseTestHelper().addCardType(card, "label", "CREDIT", "brand", false);
        app.getDatabaseTestHelper().addAcceptedCardType(Long.valueOf(accountId), card);
        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, AMOUNT, chargeStatus, returnUrl, null,
                ServicePaymentReference.of("My reference"), createdDate);
        app.getDatabaseTestHelper().updateChargeCardDetails(chargeId, "visa", null, null, null,
                null, null, null, null, null, null);
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");
        app.getDatabaseTestHelper().addEvent(chargeId, chargeStatus.getValue());

        app.getDatabaseTestHelper().addPaymentRequest(chargeId, AMOUNT, Long.valueOf(accountId), returnUrl, "Test description",
                ServicePaymentReference.of("My reference"), createdDate, externalChargeId);
        app.getDatabaseTestHelper().addChargeTransaction(chargeId, null, Long.valueOf(accountId), AMOUNT, chargeStatus, chargeId, createdDate, email);
        app.getDatabaseTestHelper().addCard(chargeId, "visa", chargeId);

        getChargeApi
                .withAccountId(accountId)
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactionsAPI()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results[0].charge_id", is(externalChargeId))
                .body("results[0].amount", is(6234))
                .body("results[0].card_details", notNullValue())
                .body("results[0].card_details.card_brand", is("Visa"))
                .body("results[0].card_details.cardholder_name", nullValue())
                .body("results[0].card_details.last_digits_card_number", nullValue())
                .body("results[0].card_details.expiry_date", nullValue());
    }

    @Test
    public void shouldFilterTransactionsByCardBrand() throws Exception {
        String searchedCardBrand = "visa";

        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now(), searchedCardBrand);
        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-2"), now(), "master-card");
        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-3"), now().minusDays(2), searchedCardBrand);

        getChargeApi
                .withAccountId(accountId)
                .withQueryParam("card_brand", searchedCardBrand)
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactionsAPI()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2))
                .body("results[0].card_details.card_brand", endsWith("Visa"))
                .body("results[1].card_details.card_brand", endsWith("Visa"));
    }

    @Test
    public void shouldFilterTransactionsByBlankCardBrand() {
        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now(), "visa");
        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-2"), now(), "master-card");

        getChargeApi
                .withAccountId(accountId)
                .withQueryParam("card_brand", "")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactionsAPI()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2))
                .body("results[0].card_details.card_brand", endsWith("Mastercard"))
                .body("results[1].card_details.card_brand", endsWith("Visa"));
    }

    @Test
    public void shouldFilterTransactionsByMultipleCardBrand() {
        String visa = "visa";
        String mastercard = "master-card";

        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now(), visa);
        addChargeAndCardDetails(AUTHORISATION_READY, ServicePaymentReference.of("ref-2"), now().minusHours(1), mastercard);
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-3"), now().minusDays(2), "american-express");

        getChargeApi
                .withQueryParams("card_brand", asList(visa, mastercard))
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactionsAPI()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2))
                .body("results[0].card_details.card_brand", endsWith("Visa"))
                .body("results[1].card_details.card_brand", endsWith("Mastercard"));
    }

    @Test
    public void shouldGetAllTransactionsForDefault_page_1_size_100_inCreationDateOrder2() {
        String id_1 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        String id_2 = addChargeAndCardDetails(AUTHORISATION_READY, ServicePaymentReference.of("ref-2"), now().plusHours(1));
        String id_3 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-3"), now().plusHours(2));
        String id_4 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-4"), now().plusHours(3));
        String id_5 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-5"), now().plusHours(4));

        int expectedTotalRows = 5;

        ValidatableResponse response = given().port(app.getLocalPort())
                .header(new Header(HttpHeaders.ACCEPT, APPLICATION_JSON))
                .get("/v1/api/accounts/{accountId}/transactions".replace("{accountId}", accountId))
                .then()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(5))
                .body("total", is(expectedTotalRows));

        List<Map<String, Object>> results = response.extract().body().jsonPath().getList("results");
        List<String> references = collect(results, "charge_id");
        assertThat(references, is(ImmutableList.of(id_5, id_4, id_3, id_2, id_1)));
    }

    @Test
    public void shouldGetTransactionsForPageAndSizeParams_inCreationDateOrder2() throws Exception {
        String id_1 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        String id_2 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-2"), now().plusHours(1));
        String id_3 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-3"), now().plusHours(2));
        String id_4 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-4"), now().plusHours(3));
        String id_5 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-5"), now().plusHours(4));
        int expectedTotalRows = 5;

        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("display_size", "2")
                .withQueryParam("page", "1")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactionsAPI()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2))
                .body("total", is(expectedTotalRows));


        List<Map<String, Object>> results = response.extract().body().jsonPath().getList("results");
        List<String> charge_ids = collect(results, "charge_id");
        assertThat(charge_ids, is(ImmutableList.of(id_5, id_4)));

        response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("display_size", "2")
                .withQueryParam("page", "2")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactionsAPI()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2))
                .body("total", is(expectedTotalRows));

        results = response.extract().body().jsonPath().getList("results");
        charge_ids = collect(results, "charge_id");
        assertThat(charge_ids, is(ImmutableList.of(id_3, id_2)));

        response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("display_size", "2")
                .withQueryParam("page", "3")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactionsAPI()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(1))
                .body("total", is(expectedTotalRows));

        results = response.extract().body().jsonPath().getList("results");
        charge_ids = collect(results, "charge_id");
        assertThat(charge_ids, is(ImmutableList.of(id_1)));
    }

    @Test
    public void shouldShowValidationsForDateAndPageDisplaySize() {
        ImmutableList<String> expectedList = ImmutableList.of(
                "query param 'from_date' not in correct format",
                "query param 'to_date' not in correct format",
                "query param 'display_size' should be a non zero positive integer",
                "query param 'page' should be a non zero positive integer");

        getChargeApi
                .withAccountId(accountId)
                .withQueryParam("to_date", "-1")
                .withQueryParam("from_date", "-1")
                .withQueryParam("page", "-1")
                .withQueryParam("display_size", "-2")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactionsAPI()
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", is(expectedList));
    }

    @Test
    public void shouldFilterTransactionsBasedOnFromAndToDates() {

        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        addChargeAndCardDetails(AUTHORISATION_READY, ServicePaymentReference.of("ref-2"), now());
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-3"), now().minusDays(2));

        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("from_date", DateTimeUtils.toUTCDateTimeString(now().minusDays(1)))
                .withQueryParam("to_date", DateTimeUtils.toUTCDateTimeString(now().plusDays(1)))
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactionsAPI()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2));

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
        assertThat(emails, contains(EMAIL, EMAIL));
    }

    @Test
    public void shouldFilterTransactionsByEmail() throws Exception {

        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        addChargeAndCardDetails(AUTHORISATION_READY, ServicePaymentReference.of("ref-2"), now());
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-3"), now().minusDays(2));

        getChargeApi
                .withAccountId(accountId)
                .withQueryParam("email", "@example.com")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactionsAPI()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(3))
                .body("results[0].email", endsWith("example.com"));
    }

    @Test
    public void shouldShowTotalCountResultsAndHalLinksForCharges() {
        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        addChargeAndCardDetails(AUTHORISATION_READY, ServicePaymentReference.of("ref-2"), now().minusDays(1));
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-3"), now().minusDays(2));
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-4"), now().minusDays(3));
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-5"), now().minusDays(4));

        assertNavigationLinksWhenNoResultFound();
        assertResultsWhenPageAndDisplaySizeNotSet();
        assertResultsAndJustSelfLinkWhenJustOneResult();
        assertResultsAndNoPrevLinkWhenOnFirstPage();
        assertResultsAndAllLinksWhenOnMiddlePage();
        assertResultsAndNoNextLinksWhenOnLastPage();
        assert404WhenRequestingInvalidPage();
        assertBadRequestForNegativePageDisplaySize();
    }

    private List<ZonedDateTime> datesFrom(List<String> createdDateStrings) {
        List<ZonedDateTime> dateTimes = newArrayList();
        createdDateStrings.forEach(aDateString -> dateTimes.add(toUTCZonedDateTime(aDateString).get()));
        return dateTimes;
    }

    private String addChargeAndCardDetails(ChargeStatus status, ServicePaymentReference reference, ZonedDateTime fromDate, String cardBrand) {
        return addChargeAndCardDetails(nextLong(), status, reference, fromDate, cardBrand);
    }

    private String addChargeAndCardDetails(Long chargeId, ChargeStatus status, ServicePaymentReference reference, ZonedDateTime fromDate, String cardBrand) {
        String externalChargeId = "charge" + chargeId;
        ChargeStatus chargeStatus = status != null ? status : AUTHORISATION_SUCCESS;
        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, AMOUNT, chargeStatus, RETURN_URL, null, reference, fromDate, EMAIL);
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");
        app.getDatabaseTestHelper().addEvent(chargeId, chargeStatus.getValue());
        app.getDatabaseTestHelper().updateChargeCardDetails(chargeId, cardBrand, "1234", "Mr. McPayment", "03/18", "line1", null, "postcode", "city", null, "country");
        app.getDatabaseTestHelper().addPaymentRequest(chargeId, AMOUNT, Long.valueOf(accountId), RETURN_URL, "some description", reference, fromDate, externalChargeId);
        app.getDatabaseTestHelper().addChargeTransaction(chargeId, null, Long.valueOf(accountId), AMOUNT, chargeStatus, chargeId, fromDate, EMAIL);
        app.getDatabaseTestHelper().addCard(chargeId, cardBrand, chargeId);

        return externalChargeId;
    }

    private List<String> collect(List<Map<String, Object>> results, String field) {
        return results.stream().map(result -> result.get(field).toString()).collect(Collectors.toList());
    }

    private String addChargeAndCardDetails(ChargeStatus status, ServicePaymentReference reference, ZonedDateTime fromDate) {
        return addChargeAndCardDetails(status, reference, fromDate, "");
    }

    private void assertResultsWhenPageAndDisplaySizeNotSet() {
        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("reference", "ref-1")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactionsAPI()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
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

    private void assertNavigationLinksWhenNoResultFound() {
        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("reference", "junk-yard")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactionsAPI()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(0))
                .body("total", is(0))
                .body("count", is(0))
                .body("_links.next_page", isEmptyOrNullString())
                .body("_links.prev_page", isEmptyOrNullString())
                .body("_links.first_page.href", is(expectedChargesLocationFor(accountId, "?reference=junk-yard&page=1&display_size=500")))
                .body("_links.last_page.href", is(expectedChargesLocationFor(accountId, "?reference=junk-yard&page=1&display_size=500")))
                .body("_links.self.href", is(expectedChargesLocationFor(accountId, "?reference=junk-yard&page=1&display_size=500")));

        List<Map<String, Object>> results = response.extract().body().jsonPath().getList("results");
        List<String> references = collect(results, "reference");
        assertTrue(results.isEmpty());
    }

    private void assertResultsAndNoPrevLinkWhenOnFirstPage() {
        // when 5 charges are there, page is 1, display-size is 2
        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("reference", "ref")
                .withQueryParam("page", "1")
                .withQueryParam("display_size", "2")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactionsAPI()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
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
                .getTransactionsAPI()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
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
                .getTransactionsAPI()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
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

    private void assertResultsAndJustSelfLinkWhenJustOneResult() {
        // when 5 charges are there, page is 1, display-size is 2
        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("reference", "ref-1")
                .withQueryParam("page", "1")
                .withQueryParam("display_size", "1")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactionsAPI()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(1))
                .body("total", is(1))
                .body("count", is(1))
                .body("_links.next_page", isEmptyOrNullString())
                .body("_links.prev_page", isEmptyOrNullString())
                .body("_links.first_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref-1&page=1&display_size=1")))
                .body("_links.last_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref-1&page=1&display_size=1")))
                .body("_links.self.href", is(expectedChargesLocationFor(accountId, "?reference=ref-1&page=1&display_size=1")));

        List<Map<String, Object>> results = response.extract().body().jsonPath().getList("results");
        List<String> references = collect(results, "reference");
        assertThat(references, containsInAnyOrder("ref-1"));
        assertThat(references, not(contains("ref-1", "ref-3", "ref-4", "ref-5")));
    }

    private void assert404WhenRequestingInvalidPage() {
        // when 5 charges are there, page is 10, display-size is 2
        ValidatableResponse response = getChargeApi
                .withAccountId(accountId)
                .withQueryParam("reference", "ref")
                .withQueryParam("page", "10")
                .withQueryParam("display_size", "2")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactionsAPI()
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
                .getTransactionsAPI()
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", is(expectedList));
    }

    private String expectedChargesLocationFor(String accountId, String queryParams) {
        return "https://localhost:" + app.getLocalPort()
                + "/v1/api/accounts/{accountId}/transactions".replace("{accountId}", accountId)
                + queryParams;
    }
}
