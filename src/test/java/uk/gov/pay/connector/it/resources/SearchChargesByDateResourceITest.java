package uk.gov.pay.connector.it.resources;

import org.apache.commons.lang.math.RandomUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.RestAssuredClient;

import javax.ws.rs.core.HttpHeaders;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static com.jayway.restassured.http.ContentType.JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.Matchers.is;

public class SearchChargesByDateResourceITest {

    private static final int AMOUNT = 6234;

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();
    private String accountId = "72332423443245";
    private RestAssuredClient connectorRestApiClient;

    @Before
    public void setupGatewayAccount() {
        app.getDatabaseTestHelper().addGatewayAccount(accountId, "sandbox");
        connectorRestApiClient = new RestAssuredClient(app.getLocalPort(), accountId);
    }

    @Test
    public void whenTheChargeCreatedDateIsBeforeTheFromDate_shouldReturnEmpty() {
        addCharge(ZonedDateTime.of(2016, 2, 2, 0, 0, 0, 0, ZoneId.of("UTC")));
        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("from_date", "2016-02-03T00:00:00Z")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(0));
    }

    @Test
    public void whenTheChargeCreatedDateIsExactlyEqualToTheFromDate_shouldReturnTheCharge() {
        addCharge(ZonedDateTime.of(2016, 2, 2, 0, 0, 0, 0, ZoneId.of("UTC")));
        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("from_date", "2016-02-02T00:00:00Z")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(1));
    }

    @Test
    public void whenTheChargeCreatedDateIsBetweenTheFromDateAndTheToDate_shouldReturnTheCharge() {
        int millis = 299000000;
        String chargeId = addCharge(ZonedDateTime.of(2016, 2, 2, 0, 0, 0, millis, ZoneId.of("UTC")));
        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("from_date", "2016-02-01T00:00:00Z")
                .withQueryParam("to_date", "2016-02-03T00:00:00Z")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(1))
                .body("results[0].charge_id", is(chargeId))
                .body("results[0].amount", is(AMOUNT))
                .body("results[0].description", is("Test description"))
                .body("results[0].state.status", is("created"))
                .body("results[0].links.size()", is(4))
                .body("results[0].return_url", is("http://return.com/1"))
                .body("results[0].created_date", is("2016-02-02T00:00:00.299Z"))
                .body("results[0].reference", is("reference"));
    }

    @Test
    public void whenTheChargeCreatedDateIsExactlyEqualToTheToDate_shouldReturnEmpty() {
        addCharge(ZonedDateTime.of(2016, 2, 2, 0, 0, 0, 0, ZoneId.of("UTC")));
        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("to_date", "2016-02-02T00:00:00Z")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(0));
    }

    @Test
    public void whenTheChargeCreatedDateIsAfterTheToDate_shouldReturnEmpty() {
        addCharge(ZonedDateTime.of(2016, 2, 2, 0, 0, 0, 0, ZoneId.of("UTC")));
        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("to_date", "2016-02-01T00:00:00Z")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(0));
    }

    @Test
    public void shouldReturnChargesWithCreatedDateGreaterThanOrEqualToFromDateAndLessThanToDate() {
        addCharge(ZonedDateTime.of(2016, 2, 1, 0, 0, 0, 0, ZoneId.of("UTC")));
        addCharge(ZonedDateTime.of(2016, 2, 2, 0, 0, 0, 0, ZoneId.of("UTC")));
        addCharge(ZonedDateTime.of(2016, 2, 3, 0, 0, 0, 0, ZoneId.of("UTC")));
        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("from_date", "2016-02-01T00:00:00Z")
                .withQueryParam("to_date", "2016-02-03T00:00:00Z")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2));
    }

    @Test
    public void whenToDateIsLessThanFromDate_shouldReturnEmpty() {
        addCharge(ZonedDateTime.of(2016, 2, 2, 0, 0, 0, 0, ZoneId.of("UTC")));
        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("from_date", "2016-02-03T00:00:00Z")
                .withQueryParam("to_date", "2016-02-01T00:00:00Z")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(0));
    }

    @Test
    public void whenFromDateIsInTheFuture_shouldReturnEmpty() {
        addCharge(ZonedDateTime.of(2016, 2, 2, 0, 0, 0, 0, ZoneId.of("UTC")));
        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("from_date", "2200-02-03T00:00:00Z")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(0));
    }

    @Test
    public void whenFromDateExactlyEqualToToDate_shouldReturnEmpty() {
        addCharge(ZonedDateTime.of(2016, 2, 1, 0, 0, 0, 0, ZoneId.of("UTC")));
        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("from_date", "2016-02-01T00:00:00Z")
                .withQueryParam("to_date", "2016-02-01T00:00:00Z")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(0));
    }

    @Test
    public void whenFromDateIsNotEmptyAndToDateIsEmpty_shouldReturnAllChargesWithCreatedDateEqualToOrGreaterThanFromDate() {
        addCharge(ZonedDateTime.of(2016, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")));
        addCharge(ZonedDateTime.of(2016, 2, 1, 0, 0, 0, 0, ZoneId.of("UTC")));
        addCharge(ZonedDateTime.of(2016, 2, 2, 0, 0, 0, 0, ZoneId.of("UTC")));
        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("from_date", "2016-02-01T00:00:00Z")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2));
    }

    @Test
    public void whenFromDateIsEmptyAndToDateIsNotEmpty_shouldReturnAllChargesWithCreatedDateLessThanToDate() {
        addCharge(ZonedDateTime.of(2016, 2, 1, 0, 0, 0, 0, ZoneId.of("UTC")));
        addCharge(ZonedDateTime.of(2016, 2, 3, 0, 0, 0, 0, ZoneId.of("UTC")));
        addCharge(ZonedDateTime.of(2016, 2, 4, 0, 0, 0, 0, ZoneId.of("UTC")));
        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("to_date", "2016-02-03T00:00:00Z")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(1));
    }

    @Test
    public void whenFromDateIsEmptyAndToDateIsEmpty_shouldReturnAllCharges() {
        addCharge(ZonedDateTime.of(2016, 2, 1, 0, 0, 0, 0, ZoneId.of("UTC")));
        addCharge(ZonedDateTime.of(2016, 2, 3, 0, 0, 0, 0, ZoneId.of("UTC")));
        addCharge(ZonedDateTime.of(2016, 2, 4, 0, 0, 0, 0, ZoneId.of("UTC")));
        connectorRestApiClient
                .withAccountId(accountId)
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(3));
    }

    private String addCharge(ZonedDateTime createdDate) {
        long chargeId = RandomUtils.nextInt();
        String externalChargeId = "charge" + chargeId;
        ChargeStatus chargeStatus = ChargeStatus.CREATED;
        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, AMOUNT, chargeStatus, "http://return.com/1", null,
                ServicePaymentReference.of("reference"), createdDate);
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");
        app.getDatabaseTestHelper().addEvent(chargeId, chargeStatus.getValue());
        return externalChargeId;
    }
}
