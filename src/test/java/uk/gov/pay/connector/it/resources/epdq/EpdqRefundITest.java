package uk.gov.pay.connector.it.resources.epdq;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.ValidatableResponse;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.util.RestAssuredClient;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.matcher.RefundsMatcher.aRefundMatching;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;

public class EpdqRefundITest extends ChargingITestBase {

    private DatabaseFixtures.TestAccount defaultTestAccount;
    private DatabaseFixtures.TestCharge defaultTestCharge;
    private DatabaseTestHelper databaseTestHelper;
    private RestAssuredClient getChargeApi = new RestAssuredClient(app, accountId);

    public EpdqRefundITest() {
        super("epdq");
    }

    @Before
    public void setup() {
        super.setup();
        databaseTestHelper = app.getDatabaseTestHelper();
        defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withAccountId(Long.valueOf(accountId))
                .withPaymentProvider("epdq");

        defaultTestCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withAmount(100L)
                .withTestAccount(defaultTestAccount)
                .withChargeStatus(CAPTURED)
                .insert();
    }

    @Test
    public void shouldBeAbleToRequestARefund_partialAmount() {
        Long refundAmount = 50L;

        epdq.mockRefundSuccess();
        ValidatableResponse validatableResponse = postRefundFor(defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount());
        String refundId = assertRefundResponseWith(defaultTestCharge.getExternalChargeId(), refundAmount, validatableResponse, ACCEPTED.getStatusCode());

        List<Map<String, Object>> refundsFoundByChargeId = databaseTestHelper.getRefundsByChargeId(defaultTestCharge.getChargeId());
        assertThat(refundsFoundByChargeId.size(), is(1));
        assertThat(refundsFoundByChargeId, hasItems(aRefundMatching(refundId, is(notNullValue()), defaultTestCharge.getChargeId(), refundAmount, "REFUND SUBMITTED")));
    }

    @Test
    public void shouldBeAbleToRequestARefund_fullAmount() {

        Long refundAmount = defaultTestCharge.getAmount();

        epdq.mockRefundSuccess();
        ValidatableResponse validatableResponse = postRefundFor(defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount());
        String refundId = assertRefundResponseWith(defaultTestCharge.getExternalChargeId(), refundAmount, validatableResponse, ACCEPTED.getStatusCode());

        List<Map<String, Object>> refundsFoundByChargeId = databaseTestHelper.getRefundsByChargeId(defaultTestCharge.getChargeId());
        assertThat(refundsFoundByChargeId.size(), is(1));
        assertThat(refundsFoundByChargeId, hasItems(aRefundMatching(refundId, is(notNullValue()), defaultTestCharge.getChargeId(), refundAmount, "REFUND SUBMITTED")));
    }

    @Test
    public void shouldBeAbleToRequestARefundIfChargeIsCaptureSubmitted() {

        DatabaseFixtures.TestCharge captureSubmittedCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withAmount(100L)
                .withTestAccount(defaultTestAccount)
                .withChargeStatus(CAPTURE_SUBMITTED)
                .insert();

        Long refundAmount = captureSubmittedCharge.getAmount();

        epdq.mockRefundSuccess();
        ValidatableResponse validatableResponse = postRefundFor(captureSubmittedCharge.getExternalChargeId(), refundAmount, captureSubmittedCharge.getAmount());
        String refundId = assertRefundResponseWith(captureSubmittedCharge.getExternalChargeId(), refundAmount, validatableResponse, ACCEPTED.getStatusCode());

        List<Map<String, Object>> refundsFoundByChargeId = databaseTestHelper.getRefundsByChargeId(captureSubmittedCharge.getChargeId());
        assertThat(refundsFoundByChargeId.size(), is(1));
        assertThat(refundsFoundByChargeId, hasItems(aRefundMatching(refundId, is(notNullValue()), captureSubmittedCharge.getChargeId(), refundAmount, "REFUND SUBMITTED")));
    }

    @Test
    public void shouldBeAbleToRequestARefund_multiplePartialAmounts_andRefundShouldBeInFullStatus() {

        Long firstRefundAmount = 80L;
        Long secondRefundAmount = 20L;
        Long chargeId = defaultTestCharge.getChargeId();
        String externalChargeId = defaultTestCharge.getExternalChargeId();

        epdq.mockRefundSuccess();
        ValidatableResponse firstValidatableResponse = postRefundFor(externalChargeId, firstRefundAmount, defaultTestCharge.getAmount());
        String firstRefundId = assertRefundResponseWith(externalChargeId, firstRefundAmount, firstValidatableResponse, ACCEPTED.getStatusCode());

        epdq.mockRefundSuccess();
        ValidatableResponse secondValidatableResponse = postRefundFor(externalChargeId, secondRefundAmount, defaultTestCharge.getAmount() - firstRefundAmount);
        String secondRefundId = assertRefundResponseWith(externalChargeId, secondRefundAmount, secondValidatableResponse, ACCEPTED.getStatusCode());

        List<Map<String, Object>> refundsFoundByChargeId = databaseTestHelper.getRefundsByChargeId(chargeId);
        assertThat(refundsFoundByChargeId.size(), is(2));

        assertThat(refundsFoundByChargeId, hasItems(
                aRefundMatching(secondRefundId, is(notNullValue()), chargeId, secondRefundAmount, "REFUND SUBMITTED"),
                aRefundMatching(firstRefundId, is(notNullValue()), chargeId, firstRefundAmount, "REFUND SUBMITTED")));

        getChargeApi.withChargeId(externalChargeId)
                .getCharge()
                .statusCode(200)
                .body("refund_summary.status", is("full"))
                .body("refund_summary.amount_available", is(0))
                .body("refund_summary.amount_submitted", is(100));
    }

    @Test
    public void shouldFailRequestingARefund_whenChargeStatusMakesItNotRefundable() {

        DatabaseFixtures.TestCharge testCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withAmount(100L)
                .withTestAccount(defaultTestAccount)
                .withChargeStatus(ENTERING_CARD_DETAILS)
                .insert();

        Long refundAmount = 20L;

        epdq.mockRefundSuccess();
        postRefundFor(testCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount())
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("reason", is("pending"))
                .body("message", is(format("Charge with id [%s] not available for refund.", testCharge.getExternalChargeId())));

        List<Map<String, Object>> refundsFoundByChargeId = databaseTestHelper.getRefundsByChargeId(defaultTestCharge.getChargeId());
        assertThat(refundsFoundByChargeId.size(), is(0));
    }

    @Test
    public void shouldFailRequestingARefund_whenChargeRefundIsFull() {

        Long refundAmount = defaultTestCharge.getAmount();
        String externalChargeId = defaultTestCharge.getExternalChargeId();
        Long chargeId = defaultTestCharge.getChargeId();

        epdq.mockRefundSuccess();
        postRefundFor(externalChargeId, refundAmount, defaultTestCharge.getAmount())
                .statusCode(ACCEPTED.getStatusCode());

        postRefundFor(externalChargeId, 1L, 0L)
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("reason", is("full"))
                .body("message", is(format("Charge with id [%s] not available for refund.", externalChargeId)));

        List<Map<String, Object>> refundsFoundByChargeId = databaseTestHelper.getRefundsByChargeId(chargeId);
        assertThat(refundsFoundByChargeId.size(), is(1));
    }

    @Test
    public void shouldFailRequestingARefund_whenAmountIsBiggerThanChargeAmount() {
        Long refundAmount = defaultTestCharge.getAmount() + 20;
        epdq.mockRefundSuccess();
        postRefundFor(defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount())
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("reason", is("amount_not_available"))
                .body("message", is("Not sufficient amount available for refund"));

        List<Map<String, Object>> refundsFoundByChargeId = databaseTestHelper.getRefundsByChargeId(defaultTestCharge.getChargeId());
        assertThat(refundsFoundByChargeId.size(), is(0));
    }

    @Test
    public void shouldFailRequestingARefund_whenAmountIsBiggerThanAllowedChargeAmount() {

        Long refundAmount = 10000001L;

        postRefundFor(defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount())
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("reason", is("amount_not_available"))
                .body("message", is("Not sufficient amount available for refund"));

        List<Map<String, Object>> refundsFoundByChargeId = databaseTestHelper.getRefundsByChargeId(defaultTestCharge.getChargeId());
        assertThat(refundsFoundByChargeId.size(), is(0));
    }

    @Test
    public void shouldFailRequestingARefund_whenAmountIsLessThanOnePence() {

        Long refundAmount = 0L;

        postRefundFor(defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount())
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("reason", is("amount_min_validation"))
                .body("message", is("Validation error for amount. Minimum amount for a refund is 1"));

        List<Map<String, Object>> refundsFoundByChargeId = databaseTestHelper.getRefundsByChargeId(defaultTestCharge.getChargeId());
        assertThat(refundsFoundByChargeId.size(), is(0));
    }

    @Test
    public void shouldFailRequestingARefund_whenAPartialRefundMakesTotalRefundedAmountBiggerThanChargeAmount() {
        Long firstRefundAmount = 80L;
        Long secondRefundAmount = 30L; // 10 more than available

        epdq.mockRefundSuccess();
        ValidatableResponse validatableResponse = postRefundFor(defaultTestCharge.getExternalChargeId(), firstRefundAmount, defaultTestCharge.getAmount());
        String firstRefundId = assertRefundResponseWith(defaultTestCharge.getExternalChargeId(), firstRefundAmount, validatableResponse, ACCEPTED.getStatusCode());

        List<Map<String, Object>> refundsFoundByChargeId = databaseTestHelper.getRefundsByChargeId(defaultTestCharge.getChargeId());
        assertThat(refundsFoundByChargeId.size(), is(1));
        assertThat(refundsFoundByChargeId, hasItems(aRefundMatching(firstRefundId, is(notNullValue()), defaultTestCharge.getChargeId(), firstRefundAmount, "REFUND SUBMITTED")));

        epdq.mockRefundSuccess();
        postRefundFor(defaultTestCharge.getExternalChargeId(), secondRefundAmount, defaultTestCharge.getAmount() - firstRefundAmount)
                .statusCode(400)
                .body("reason", is("amount_not_available"))
                .body("message", is("Not sufficient amount available for refund"));

        List<Map<String, Object>> refundsFoundByChargeId1 = databaseTestHelper.getRefundsByChargeId(defaultTestCharge.getChargeId());
        assertThat(refundsFoundByChargeId1.size(), is(1));
        assertThat(refundsFoundByChargeId1, hasItems(aRefundMatching(firstRefundId, is(notNullValue()), defaultTestCharge.getChargeId(), firstRefundAmount, "REFUND SUBMITTED")));
    }

    @Test
    public void shouldFailRequestingARefund_whenGatewayOperationFails() {
        Long refundAmount = defaultTestCharge.getAmount();

        epdq.mockRefundError();
        postRefundFor(defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount())
                .statusCode(INTERNAL_SERVER_ERROR.getStatusCode())
                .body("message",
                        is("ePDQ refund response (PAYID: 0, STATUS: 0, NCERROR: 50001111, " +
                                "NCERRORPLUS: An error has occurred; please try again later. " +
                                "If you are the owner or the integrator of this website, " +
                                "please log into the  back office to see the details of the error.)"));

        List<Map<String, Object>> refundsFoundByChargeId = databaseTestHelper.getRefundsByChargeId(defaultTestCharge.getChargeId());
        assertThat(refundsFoundByChargeId.size(), is(1));
        assertThat(refundsFoundByChargeId, containsInAnyOrder(
                allOf(
                        hasEntry("amount", (Object) refundAmount),
                        hasEntry("status", RefundStatus.REFUND_ERROR.getValue()),
                        hasEntry("charge_id", (Object) defaultTestCharge.getChargeId())
                )));
    }

    @Test
    public void shouldBeAbleRetrieveARefund() {
        DatabaseFixtures.TestRefund testRefund = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withTestCharge(defaultTestCharge)
                .withType(RefundStatus.REFUND_SUBMITTED)
                .withReference(randomAlphanumeric(10))
                .insert();

        ValidatableResponse validatableResponse = getRefundFor(
                defaultTestAccount.getAccountId(), defaultTestCharge.getExternalChargeId(), testRefund.getExternalRefundId());
        String refundId = assertRefundResponseWith(defaultTestCharge.getExternalChargeId(), testRefund.getAmount(), validatableResponse, OK.getStatusCode());


        List<Map<String, Object>> refundsFoundByChargeId = databaseTestHelper.getRefundsByChargeId(defaultTestCharge.getChargeId());
        assertThat(refundsFoundByChargeId.size(), is(1));
        assertThat(refundsFoundByChargeId, hasItems(aRefundMatching(refundId, is(notNullValue()), defaultTestCharge.getChargeId(), testRefund.getAmount(), "REFUND SUBMITTED")));
    }

    @Test
    public void shouldBeAbleToRetrieveAllRefundsForACharge() {

        DatabaseFixtures.TestRefund testRefund1 = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withAmount(10L)
                .withCreatedDate(ZonedDateTime.of(2016, 8, 1, 0, 0, 0, 0, ZoneId.of("UTC")))
                .withTestCharge(defaultTestCharge)
                .withType(RefundStatus.REFUND_SUBMITTED)
                .insert();

        DatabaseFixtures.TestRefund testRefund2 = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withAmount(20L)
                .withCreatedDate(ZonedDateTime.of(2016, 8, 2, 0, 0, 0, 0, ZoneId.of("UTC")))
                .withTestCharge(defaultTestCharge)
                .withType(RefundStatus.REFUND_SUBMITTED)
                .insert();

        String paymentUrl = format("https://localhost:%s/v1/api/accounts/%s/charges/%s",
                app.getLocalPort(), defaultTestAccount.getAccountId(), defaultTestCharge.getExternalChargeId());

        getRefundsFor(defaultTestAccount.getAccountId(),
                defaultTestCharge.getExternalChargeId())
                .statusCode(OK.getStatusCode())
                .body("payment_id", is(defaultTestCharge.getExternalChargeId()))
                .body("_links.self.href", is(paymentUrl + "/refunds"))
                .body("_links.payment.href", is(paymentUrl))
                .body("_embedded.refunds", hasSize(2))
                .body("_embedded.refunds[0].refund_id", is(testRefund1.getExternalRefundId()))
                .body("_embedded.refunds[0].amount", is(10))
                .body("_embedded.refunds[0].status", is("submitted"))
                .body("_embedded.refunds[0].created_date", is("2016-08-01T00:00:00Z"))
                .body("_embedded.refunds[0]._links.self.href", is(paymentUrl + "/refunds/" + testRefund1.getExternalRefundId()))
                .body("_embedded.refunds[0]._links.payment.href", is(paymentUrl))
                .body("_embedded.refunds[1].refund_id", is(testRefund2.getExternalRefundId()))
                .body("_embedded.refunds[1].amount", is(20))
                .body("_embedded.refunds[1].status", is("submitted"))
                .body("_embedded.refunds[1].created_date", is("2016-08-02T00:00:00Z"))
                .body("_embedded.refunds[1]._links.self.href", is(paymentUrl + "/refunds/" + testRefund2.getExternalRefundId()))
                .body("_embedded.refunds[1]._links.payment.href", is(paymentUrl));
    }

    @Test
    public void shouldFailRetrieveARefund_whenNonExistentAccountId() {
        DatabaseFixtures.TestRefund testRefund = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withTestCharge(defaultTestCharge)
                .withType(RefundStatus.REFUND_SUBMITTED)
                .insert();

        Long nonExistentAccountId = 999L;

        ValidatableResponse validatableResponse = getRefundFor(
                nonExistentAccountId, defaultTestCharge.getExternalChargeId(), testRefund.getExternalRefundId());

        validatableResponse.statusCode(NOT_FOUND.getStatusCode())
                .body("message", is(format("Charge with id [%s] not found.", defaultTestCharge.getExternalChargeId())));
    }

    @Test
    public void shouldFailRetrieveARefund_whenNonExistentChargeId() {
        DatabaseFixtures.TestRefund testRefund = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withTestCharge(defaultTestCharge)
                .withType(RefundStatus.REFUND_SUBMITTED)
                .insert();

        String nonExistentChargeId = "999";

        ValidatableResponse validatableResponse = getRefundFor(
                defaultTestAccount.getAccountId(), nonExistentChargeId, testRefund.getExternalRefundId());

        validatableResponse.statusCode(NOT_FOUND.getStatusCode())
                .body("message", is(format("Charge with id [%s] not found.", nonExistentChargeId)));
    }

    @Test
    public void shouldFailRetrieveARefund_whenNonExistentRefundId() {
        DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withTestCharge(defaultTestCharge)
                .withType(RefundStatus.REFUND_SUBMITTED)
                .insert();

        String nonExistentRefundId = "999";

        ValidatableResponse validatableResponse = getRefundFor(
                defaultTestAccount.getAccountId(), defaultTestCharge.getExternalChargeId(), nonExistentRefundId);

        validatableResponse.statusCode(NOT_FOUND.getStatusCode())
                .body("message", is(format("Refund with id [%s] not found.", nonExistentRefundId)));
    }

    private ValidatableResponse postRefundFor(String chargeId, Long refundAmount, Long refundAmountAvlbl) {
        ImmutableMap<String, Long> refundData = ImmutableMap.of("amount", refundAmount, "refund_amount_available", refundAmountAvlbl);
        String refundPayload = new Gson().toJson(refundData);

        return givenSetup()
                .body(refundPayload)
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .post("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds"
                        .replace("{accountId}", accountId)
                        .replace("{chargeId}", chargeId))
                .then();
    }

    private ValidatableResponse getRefundsFor(Long accountId, String chargeId) {
        return givenSetup()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .get("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds"
                        .replace("{accountId}", accountId.toString())
                        .replace("{chargeId}", chargeId))
                .then();
    }

    private ValidatableResponse getRefundFor(Long accountId, String chargeId, String refundId) {
        return givenSetup()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .get("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds/{refundId}"
                        .replace("{accountId}", accountId.toString())
                        .replace("{chargeId}", chargeId)
                        .replace("{refundId}", refundId))
                .then();
    }

    private String assertRefundResponseWith(String externalChargeId, Long refundAmount, ValidatableResponse validatableResponse, int expectedStatusCode) {
        ValidatableResponse response = validatableResponse
                .statusCode(expectedStatusCode)
                .body("refund_id", is(notNullValue()))
                .body("amount", is(refundAmount.intValue()))
                .body("status", is("submitted"))
                .body("created_date", is(notNullValue()));

        String paymentUrl = format("https://localhost:%s/v1/api/accounts/%s/charges/%s",
                app.getLocalPort(), defaultTestAccount.getAccountId(), externalChargeId);

        String refundId = response.extract().path("refund_id");
        response.body("_links.self.href", is(paymentUrl + "/refunds/" + refundId))
                .body("_links.payment.href", is(paymentUrl));

        return refundId;
    }

}
