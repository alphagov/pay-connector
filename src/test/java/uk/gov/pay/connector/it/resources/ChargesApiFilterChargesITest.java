package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableList;
import com.jayway.restassured.response.ValidatableResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import javax.ws.rs.core.HttpHeaders;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.jayway.restassured.http.ContentType.JSON;
import static java.time.ZonedDateTime.now;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Arrays.asList;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.lang.math.RandomUtils.nextInt;
import static org.exparity.hamcrest.date.ZonedDateTimeMatchers.within;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.commons.model.ApiResponseDateTimeFormatter.ISO_INSTANT_MILLISECOND_PRECISION;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_SUBMITTED;
import static uk.gov.pay.connector.util.NumberMatcher.isNumber;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class ChargesApiFilterChargesITest extends ChargingITestBase {
    private static final String PROVIDER_NAME = "sandbox";
    private static final String JSON_CHARGE_KEY = "charge_id";
    private static final String JSON_STATE_KEY = "state.status";

    public ChargesApiFilterChargesITest() {
        super(PROVIDER_NAME);
    }

    @Test
    public void shouldFilterTransactionsBasedOnFromAndToDates() {

        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        addChargeAndCardDetails(AUTHORISATION_READY, ServicePaymentReference.of("ref-2"), now());
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-3"), now().minusDays(2));

        ValidatableResponse response = connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("from_date", ISO_INSTANT_MILLISECOND_PRECISION.format(now().minusDays(1)))
                .withQueryParam("to_date", ISO_INSTANT_MILLISECOND_PRECISION.format(now().plusDays(1)))
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2))
                .body("results[0].settlement_summary.capture_submit_time", nullValue())
                .body("results[0].settlement_summary.captured_time", nullValue())
                .body("results[0].refund_summary.amount_submitted", is(0))
                .body("results[0].refund_summary.amount_available", isNumber(AMOUNT))
                .body("results[0].refund_summary.status", is("pending"))
                .body("results[1].settlement_summary.capture_submit_time", nullValue())
                .body("results[1].settlement_summary.captured_time", nullValue())
                .body("results[1].refund_summary.amount_submitted", is(0))
                .body("results[1].refund_summary.amount_available", isNumber(AMOUNT))
                .body("results[1].refund_summary.status", is("pending"));

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
    public void shouldFilterTransactionsByEmail() {

        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        addChargeAndCardDetails(AUTHORISATION_READY, ServicePaymentReference.of("ref-2"), now());
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-3"), now().minusDays(2));

        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("email", "@example.com")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(3))
                .body("results[0].email", endsWith("example.com"));
    }

    @Test
    public void shouldFilterTransactionsByCardHolderName() {

        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        addChargeAndCardDetails(AUTHORISATION_READY, ServicePaymentReference.of("ref-2"), now());
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-3"), now().minusDays(2));

        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("cardholder_name", "McPayment")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(3))
                .body("results[0].card_details.cardholder_name", is("Mr. McPayment"));
    }

    @Test
    public void shouldFilterTransactionsByCardBrand() {
        String searchedCardBrand = "visa";

        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now(), searchedCardBrand);
        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-2"), now(), "master-card");
        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-3"), now().minusDays(2), searchedCardBrand);

        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("card_brand", searchedCardBrand)
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
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

        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("card_brand", "")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
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

        connectorRestApiClient
                .withQueryParams("card_brand", asList(visa, mastercard))
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2))
                .body("results[0].card_details.card_brand", endsWith("Visa"))
                .body("results[1].card_details.card_brand", endsWith("Mastercard"));
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
                .getChargesV1()
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", is(expectedList));
    }

    @Test
    public void shouldGetChargesForPageAndSizeParams_inCreationDateOrder() {
        String id_1 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        String id_2 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-2"), now().plusHours(1));
        String id_3 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-3"), now().plusHours(2));
        String id_4 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-4"), now().plusHours(3));
        String id_5 = addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-5"), now().plusHours(4));

        ValidatableResponse response = connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("display_size", "2")
                .withQueryParam("page", "1")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2));

        List<Map<String, Object>> results = response.extract().body().jsonPath().getList("results");
        List<String> charge_ids = collect(results, "charge_id");
        assertThat(charge_ids, is(ImmutableList.of(id_5, id_4)));

        response = connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("display_size", "2")
                .withQueryParam("page", "2")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2));

        results = response.extract().body().jsonPath().getList("results");
        charge_ids = collect(results, "charge_id");
        assertThat(charge_ids, is(ImmutableList.of(id_3, id_2)));

        response = connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("display_size", "2")
                .withQueryParam("page", "3")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(1));

        results = response.extract().body().jsonPath().getList("results");
        charge_ids = collect(results, "charge_id");
        assertThat(charge_ids, is(ImmutableList.of(id_1)));
    }
    
    @Test
    public void shouldFilterChargeStatusToReturnInProgressIfInternalStatusIsAuthorised() {

        long chargeId = nextInt();
        String externalChargeId = "charge1";

        databaseTestHelper.addCharge(chargeId, externalChargeId, accountId, AMOUNT, AUTHORISATION_SUCCESS, RETURN_URL, null);
        databaseTestHelper.addToken(chargeId, "tokenId");

        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(externalChargeId))
                .body(JSON_STATE_KEY, is(EXTERNAL_SUBMITTED.getStatus()));
    }

    @Test
    public void shouldFilterTransactionsByFirstDigitsCardNumber() {

        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        addChargeAndCardDetails(AUTHORISATION_READY, ServicePaymentReference.of("ref-2"), now());
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-3"), now().minusDays(2));

        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("first_digits_card_number", "123456")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(3))
                .body("results[0].card_details.first_digits_card_number", is("123456"));
    }

    @Test
    public void shouldIgnoreFirstDigitsCardNumberIfBlank() {

        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        addChargeAndCardDetails(AUTHORISATION_READY, ServicePaymentReference.of("ref-2"), now());
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-3"), now().minusDays(2));

        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("first_digits_card_number", "")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(3))
                .body("results[0].card_details.first_digits_card_number", is("123456"));
    }

    @Test
    public void shouldIgnoreFirstDigitsCardNumberIfPartial() {

        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        addChargeAndCardDetails(AUTHORISATION_READY, ServicePaymentReference.of("ref-2"), now());
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-3"), now().minusDays(2));

        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("first_digits_card_number", "12")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(3))
                .body("results[0].card_details.first_digits_card_number", is("123456"));
    }

    @Test
    public void shouldFilterTransactionsByLastDigitsCardNumber() {

        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        addChargeAndCardDetails(AUTHORISATION_READY, ServicePaymentReference.of("ref-2"), now());
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-3"), now().minusDays(2));

        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("last_digits_card_number", "1234")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(3))
                .body("results[0].card_details.last_digits_card_number", is("1234"));
    }

    @Test
    public void shouldIgnoreLastDigitsCardNumberIfBlank() {

        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        addChargeAndCardDetails(AUTHORISATION_READY, ServicePaymentReference.of("ref-2"), now());
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-3"), now().minusDays(2));

        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("last_digits_card_number", "")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(3))
                .body("results[0].card_details.last_digits_card_number", is("1234"));
    }

    @Test
    public void shouldIgnoreLastDigitsCardNumberIfPartial() {

        addChargeAndCardDetails(CREATED, ServicePaymentReference.of("ref-1"), now());
        addChargeAndCardDetails(AUTHORISATION_READY, ServicePaymentReference.of("ref-2"), now());
        addChargeAndCardDetails(CAPTURED, ServicePaymentReference.of("ref-3"), now().minusDays(2));

        connectorRestApiClient
                .withAccountId(accountId)
                .withQueryParam("first_digits_card_number", "12")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .getChargesV1()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(3))
                .body("results[0].card_details.first_digits_card_number", is("123456"));
    }

}
