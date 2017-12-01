package uk.gov.pay.connector.it.resources;

import org.apache.commons.lang.math.RandomUtils;
import org.junit.Test;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.util.RestAssuredClient;

import javax.ws.rs.core.HttpHeaders;
import java.time.ZonedDateTime;

import static com.jayway.restassured.http.ContentType.JSON;
import static java.time.ZonedDateTime.now;
import static java.time.temporal.ChronoUnit.HOURS;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static uk.gov.pay.connector.matcher.ZoneDateTimeAsStringWithinMatcher.isWithin;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.resources.ApiPaths.CHARGES_API_PATH;

public class TransactionsApiResourceDropwizardITest extends ChargingITestBase {

    private static final String PROVIDER_NAME = "sandbox";

    private RestAssuredClient getChargeApi = new RestAssuredClient(app, accountId);
    private String lastDigitsCardNumber;
    private String cardHolderName;
    private String expiryDate;

    public TransactionsApiResourceDropwizardITest() {
        super(PROVIDER_NAME);
    }

    /**
     * Contract (Selfservice POV) (Transactions List & CSV)
     * ----------------------------------------------------
     * transaction_type
     * state.status
     * state.finished
     * state.code
     * state.message
     * amount
     * created_date
     * email
     * reference
     * description
     * card_details.card_brand
     * card_details.cardholder_name
     * card_details.expiry_date
     * card_details.last_digits_card_number
     * gateway_transaction_id
     * charge_id
     * <p>
     * Pagination
     */
    @Test
    public void shouldGetExpectedTransactionsByChargeReferenceAndStatusesForBothChargesAndRefunds() {

        String returnUrl = "http://service.url/success-page/";
        String email = randomAlphabetic(242) + "@example.com";
        long chargeId2 = nextLong();

        String transactionIdCharge1 = "transaction-id-ref-3-que";
        String transactionIdCharge2 = "transaction-id-ref-3";
        String externalChargeId1 = addChargeAndCardDetails(nextLong(), CREATED, "ref-3-que", transactionIdCharge1, now(), "", returnUrl, email);
        addChargeAndCardDetails(nextLong(), CAPTURED, "ref-7", "transaction-id-ref-7", now(), "master-card", returnUrl, email);
        String externalChargeId2 = addChargeAndCardDetails(chargeId2, CAPTURED, "ref-3", transactionIdCharge2, now().minusDays(2), "visa", returnUrl, email);

        app.getDatabaseTestHelper().addRefund(RandomUtils.nextInt(), randomAlphanumeric(10), "refund-1-provider-reference", 1L, RefundStatus.REFUND_SUBMITTED.getValue(), chargeId2, now().minusHours(2));
        app.getDatabaseTestHelper().addRefund(RandomUtils.nextInt(), randomAlphanumeric(10), "refund-2-provider-reference", 2L, RefundStatus.REFUNDED.getValue(), chargeId2, now().minusHours(3));

        getChargeApi
                .withAccountId(accountId)
                .withQueryParam("reference", "ref-3")
                .withQueryParam("page", "1")
                .withQueryParam("display_size", "2")
                .withQueryParam("payment_states", "success,created")
                .withQueryParam("refund_states", "submitted")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .withHeader("features", "REFUNDS_IN_TX_LIST")
                .getTransactions()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2))
                .body("total", is(3))
                .body("count", is(2))
                .body("page", is(1))
                .body("_links.next_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref-3&page=2&display_size=2&payment_states=created%2Csuccess&refund_states=submitted")))
                .body("_links.prev_page", isEmptyOrNullString())
                .body("_links.first_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref-3&page=1&display_size=2&payment_states=created%2Csuccess&refund_states=submitted")))
                .body("_links.last_page.href", is(expectedChargesLocationFor(accountId, "?reference=ref-3&page=2&display_size=2&payment_states=created%2Csuccess&refund_states=submitted")))
                .body("_links.self.href", is(expectedChargesLocationFor(accountId, "?reference=ref-3&page=1&display_size=2&payment_states=created%2Csuccess&refund_states=submitted")))

                .body("results[0].transaction_type", is("charge"))
                .body("results[0].gateway_transaction_id", is(transactionIdCharge1))
                .body("results[0].charge_id", is(externalChargeId1))
                .body("results[0].amount", is(6234))
                .body("results[0].description", is("Test description"))
                .body("results[0].reference", is("ref-3-que"))
                .body("results[0].state.finished", is(false))
                .body("results[0].state.status", is("created"))
                .body("results[0].state.code", is(isEmptyOrNullString()))
                .body("results[0].state.message", is(isEmptyOrNullString()))
                .body("results[0].card_details.card_brand", is(nullValue()))
                .body("results[0].card_details.cardholder_name", is(cardHolderName))
                .body("results[0].card_details.expiry_date", is(expiryDate))
                .body("results[0].card_details.last_digits_card_number", is(lastDigitsCardNumber))
                .body("results[0].created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3}Z"))
                .body("results[0].created_date", isWithin(3, HOURS)) // The refund CREATED is the most recent

                .body("results[1].transaction_type", is("refund"))
                .body("results[1].gateway_transaction_id", is(transactionIdCharge2))
                .body("results[1].charge_id", is(externalChargeId2))
                .body("results[1].amount", is(1))
                .body("results[1].description", is("Test description"))
                .body("results[1].reference", is("ref-3"))
                .body("results[1].email", is(email))
                .body("results[1].state.finished", is(false))
                .body("results[1].state.status", is("submitted"))
                .body("results[1].state.code", is(isEmptyOrNullString()))
                .body("results[1].state.message", is(isEmptyOrNullString()))
                .body("results[1].card_details.card_brand", is("Visa"))
                .body("results[1].card_details.cardholder_name", is(cardHolderName))
                .body("results[1].card_details.expiry_date", is(expiryDate))
                .body("results[1].card_details.last_digits_card_number", is(lastDigitsCardNumber))
                .body("results[1].created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3}Z"))
                .body("results[1].created_date", isWithin(3, HOURS)); // The refund CREATED is the most recent
    }

    @Test
    public void shouldGetExpectedChargeWhenOnlySpecifiedPaymentStates() {

        String returnUrl = "http://service.url/success-page/";
        String email = randomAlphabetic(242) + "@example.com";
        long chargeId2 = nextLong();

        String transactionIdCharge2 = "transaction-id-ref-3";
        String transactionIdCharge1 = "transaction-id-ref-3-que";
        String referenceCharge1 = "ref-3-que";
        String referenceCharge2 = "ref-3";
        String externalChargeId1 = addChargeAndCardDetails(nextLong(), CREATED, referenceCharge1, transactionIdCharge1, now(), "", returnUrl, email);
        addChargeAndCardDetails(nextLong(), CAPTURED, "ref-7", "transaction-id-ref-7", now(), "master-card", returnUrl, email);
        String externalChargeId2 = addChargeAndCardDetails(chargeId2, CAPTURED, referenceCharge2, transactionIdCharge2, now().minusDays(2), "visa", returnUrl, email);

        app.getDatabaseTestHelper().addRefund(RandomUtils.nextInt(), randomAlphanumeric(10), "refund-1-provider-reference", 1L, RefundStatus.REFUND_SUBMITTED.getValue(), chargeId2, now().minusHours(2));
        app.getDatabaseTestHelper().addRefund(RandomUtils.nextInt(), randomAlphanumeric(10), "refund-2-provider-reference", 2L, RefundStatus.REFUNDED.getValue(), chargeId2, now().minusHours(3));

        getChargeApi
                .withAccountId(accountId)
                .withQueryParam("reference", "ref-3")
                .withQueryParam("page", "1")
                .withQueryParam("display_size", "2")
                .withQueryParam("payment_states", "success,created")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .withHeader("features", "REFUNDS_IN_TX_LIST")
                .getTransactions()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(2))
                .body("total", is(2))
                .body("count", is(2))
                .body("page", is(1))
                .body("results[0].transaction_type", is("charge"))
                .body("results[0].gateway_transaction_id", is(transactionIdCharge1))
                .body("results[0].charge_id", is(externalChargeId1))
                .body("results[0].reference", is(referenceCharge1))
                .body("results[1].transaction_type", is("charge"))
                .body("results[1].gateway_transaction_id", is(transactionIdCharge2))
                .body("results[1].charge_id", is(externalChargeId2))
                .body("results[1].reference", is(referenceCharge2));
    }

    @Test
    public void shouldGetExpectedChargeWhenOnlySpecifiedRefundStates() {

        String returnUrl = "http://service.url/success-page/";
        String email = randomAlphabetic(242) + "@example.com";
        long chargeId2 = nextLong();

        String transactionIdCharge2 = "transaction-id-ref-3";
        addChargeAndCardDetails(nextLong(), CREATED, "ref-3-que", "transaction-id-ref-3-que", now(), "", returnUrl, email);
        addChargeAndCardDetails(nextLong(), CAPTURED, "ref-7", "transaction-id-ref-7", now(), "master-card", returnUrl, email);
        String externalChargeId2 = addChargeAndCardDetails(chargeId2, CAPTURED, "ref-3", transactionIdCharge2, now().minusDays(2), "visa", returnUrl, email);

        app.getDatabaseTestHelper().addRefund(RandomUtils.nextInt(), randomAlphanumeric(10), "refund-1-provider-reference", 1L, RefundStatus.REFUND_SUBMITTED.getValue(), chargeId2, now().minusHours(2));
        app.getDatabaseTestHelper().addRefund(RandomUtils.nextInt(), randomAlphanumeric(10), "refund-2-provider-reference", 2L, RefundStatus.REFUNDED.getValue(), chargeId2, now().minusHours(3));

        getChargeApi
                .withAccountId(accountId)
                .withQueryParam("reference", "ref-3")
                .withQueryParam("page", "1")
                .withQueryParam("display_size", "2")
                .withQueryParam("refund_states", "success")
                .withHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .withHeader("features", "REFUNDS_IN_TX_LIST")
                .getTransactions()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body("results.size()", is(1))
                .body("total", is(1))
                .body("count", is(1))
                .body("page", is(1))
                .body("results[0].transaction_type", is("refund"))
                .body("results[0].gateway_transaction_id", is(transactionIdCharge2))
                .body("results[0].charge_id", is(externalChargeId2));
    }
    private String addChargeAndCardDetails(Long chargeId, ChargeStatus status, String reference, String transactionId, ZonedDateTime fromDate, String cardBrand, String returnUrl, String email) {
        String externalChargeId = "charge" + chargeId;
        ChargeStatus chargeStatus = status != null ? status : AUTHORISATION_SUCCESS;
        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, AMOUNT, chargeStatus, returnUrl, transactionId, reference, fromDate, email);
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");
        app.getDatabaseTestHelper().addEvent(chargeId, chargeStatus.getValue());
        lastDigitsCardNumber = "1234";
        cardHolderName = "Mr. McPayment";
        expiryDate = "03/18";
        app.getDatabaseTestHelper().updateChargeCardDetails(chargeId, cardBrand, lastDigitsCardNumber, cardHolderName, expiryDate, "line1", null, "postcode", "city", null, "country");
        return externalChargeId;
    }

    private String expectedChargesLocationFor(String accountId, String queryParams) {
        return "https://localhost:" + app.getLocalPort()
                + CHARGES_API_PATH.replace("{accountId}", accountId)
                + queryParams;
    }
}
