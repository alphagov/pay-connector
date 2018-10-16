package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableList;
import com.jayway.restassured.response.Header;
import com.jayway.restassured.response.ValidatableResponse;
import org.junit.Test;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.it.base.ChargingITestBase;

import javax.ws.rs.core.HttpHeaders;
import java.util.List;
import java.util.Map;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static java.time.ZonedDateTime.now;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;

public class ChargesApiResourceJsonPaginationITest extends ChargingITestBase {
    private static final String PROVIDER_NAME = "sandbox";

    public ChargesApiResourceJsonPaginationITest() {
        super(PROVIDER_NAME);
    }

    @Test
    public void shouldReturn404OnGetTransactionsWhenAccountIdIsNonNumeric() {
        connectorRestApiClient
                .withAccountId("invalidAccountId")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactionsAPI()
                .contentType(JSON)
                .statusCode(NOT_FOUND.getStatusCode())
                .body("code", is(404))
                .body("message", is("HTTP 404 Not Found"));
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
    public void shouldGetTransactionsForPageAndSizeParams_inCreationDateOrder2() {
        String id_1 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        String id_2 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-2"), now().plusHours(1));
        String id_3 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-3"), now().plusHours(2));
        String id_4 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-4"), now().plusHours(3));
        String id_5 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-5"), now().plusHours(4));
        int expectedTotalRows = 5;

        ValidatableResponse response = connectorRestApiClient
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

        response = connectorRestApiClient
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

        response = connectorRestApiClient
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

        connectorRestApiClient
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
    public void shouldGetAllTransactionsForDefault_page_1_size_100_inCreationDateOrder() {
        String id_1 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        String id_2 = addChargeAndCardDetails(AUTHORISATION_READY, ServicePaymentReference.of("ref-2"), now().plusHours(1));
        String id_3 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-3"), now().plusHours(2));
        String id_4 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-4"), now().plusHours(3));
        String id_5 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-5"), now().plusHours(4));

        ValidatableResponse response = connectorRestApiClient
                .withAccountId(accountId)
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(5));

        List<Map<String, Object>> results = response.extract().body().jsonPath().getList("results");
        List<String> references = collect(results, "charge_id");
        assertThat(references, is(ImmutableList.of(id_5, id_4, id_3, id_2, id_1)));
    }

    @Test
    public void shouldGetBadRequestForNegativePageDisplaySize() {
        ImmutableList<String> expectedList = ImmutableList.of(
                "query param 'display_size' should be a non zero positive integer",
                "query param 'page' should be a non zero positive integer");

        connectorRestApiClient
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

    @Test
    public void shouldGetResults_whenPageAndDisplaySizeNotSet() {
        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        addChargeAndCardDetails(AUTHORISATION_READY, ServicePaymentReference.of("ref-2"), now().minusDays(1));
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-3"), now().minusDays(2));
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-4"), now().minusDays(3));
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-5"), now().minusDays(4));
        ValidatableResponse response = connectorRestApiClient
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

    @Test
    public void shouldGetNoNavigationLinks_whenNoResultFound() {
        ValidatableResponse response = connectorRestApiClient
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
        assertTrue(results.isEmpty());
    }

    @Test
    public void shouldGetResultsAndNoPrevLink_whenOnFirstPage() {
        setUpChargeAndCardDetails();
        // when 5 charges are there, page is 1, display-size is 2
        ValidatableResponse response = connectorRestApiClient
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

    @Test
    public void shouldGetResultsAndAllLinks_whenOnMiddlePage() {
        // when 5 charges are there, page is 2, display-size is 2
        setUpChargeAndCardDetails();
        ValidatableResponse response = connectorRestApiClient
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

    @Test
    public void shouldGetResultsAndNoNextLinks_whenOnLastPage() {
        // when 5 charges are there, page is 3, display-size is 2
        setUpChargeAndCardDetails();
        ValidatableResponse response = connectorRestApiClient
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

    @Test
    public void shouldGetJustSelfLink_whenJustOneResult() {
        // when 5 charges are there, page is 1, display-size is 2
        setUpChargeAndCardDetails();
        ValidatableResponse response = connectorRestApiClient
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

    @Test
    public void shouldReceive404_whenRequestingInvalidPage() {
        // when 5 charges are there, page is 10, display-size is 2
        setUpChargeAndCardDetails();
        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("reference", "ref")
                .withQueryParam("page", "10")
                .withQueryParam("display_size", "2")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getTransactionsAPI()
                .statusCode(NOT_FOUND.getStatusCode())
                .body("message", is("the requested page not found"));
    }

    private void setUpChargeAndCardDetails() {
        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        addChargeAndCardDetails(AUTHORISATION_READY, ServicePaymentReference.of("ref-2"), now().minusDays(1));
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-3"), now().minusDays(2));
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-4"), now().minusDays(3));
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-5"), now().minusDays(4));
    }

    private String expectedChargesLocationFor(String accountId, String queryParams) {
        return "https://localhost:" + app.getLocalPort()
                + "/v1/api/accounts/{accountId}/transactions".replace("{accountId}", accountId)
                + queryParams;
    }
}
