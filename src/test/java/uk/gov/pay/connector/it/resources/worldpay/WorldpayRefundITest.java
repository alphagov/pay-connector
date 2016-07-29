package uk.gov.pay.connector.it.resources.worldpay;

import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.ValidatableResponse;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.it.base.CardResourceITestBase;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.util.Map;

import static java.lang.String.format;
import static java.time.temporal.ChronoUnit.SECONDS;
import static javax.ws.rs.core.Response.Status.*;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static uk.gov.pay.connector.matcher.ZoneDateTimeAsStringWithinMatcher.isWithin;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.resources.ApiPaths.REFUNDS_API_PATH;
import static uk.gov.pay.connector.resources.ApiPaths.REFUND_API_PATH;

public class WorldpayRefundITest extends CardResourceITestBase {

    private DatabaseFixtures.TestAccount defaultTestAccount;
    private DatabaseFixtures.TestCharge defaultTestCharge;
    private DatabaseTestHelper databaseTestHelper;

    public WorldpayRefundITest() {
        super("worldpay");
    }

    @Before
    public void setUp() throws Exception {
        databaseTestHelper = app.getDatabaseTestHelper();
        defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withAccountId(Long.valueOf(accountId));

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

        ValidatableResponse validatableResponse = postRefundFor(refundAmount);
        String refundId = assertRefundResponseWith(refundAmount, validatableResponse, ACCEPTED.getStatusCode());

        assertRefundDatabaseRecord(refundAmount, refundId);
    }

    @Test
    public void shouldBeAbleToRequestARefund_fullAmount() {
        Long refundAmount = defaultTestCharge.getAmount();

        ValidatableResponse validatableResponse = postRefundFor(refundAmount);
        String refundId = assertRefundResponseWith(Long.valueOf(defaultTestCharge.getAmount()), validatableResponse, ACCEPTED.getStatusCode());
        assertRefundDatabaseRecord(defaultTestCharge.getAmount(), refundId);
    }

    @Test
    public void shouldBeAbleToRequestARefund_multiplePartialAmounts() {
        Long firstRefundAmount = 80L;
        Long secondRefundAmount = 20L;

        ValidatableResponse firstValidatableResponse = postRefundFor(firstRefundAmount);
        String firstRefundId = assertRefundResponseWith(firstRefundAmount, firstValidatableResponse, ACCEPTED.getStatusCode());
        assertRefundDatabaseRecord(firstRefundAmount, firstRefundId);

        ValidatableResponse secondValidatableResponse = postRefundFor(secondRefundAmount);
        String secondRefundId = assertRefundResponseWith(secondRefundAmount, secondValidatableResponse, ACCEPTED.getStatusCode());

        java.util.List<Map<String, Object>> refundsFoundByChargeId = databaseTestHelper.getRefundsByChargeId(defaultTestCharge.getChargeId());
        assertThat(refundsFoundByChargeId.size(), is(2));
        assertThat(refundsFoundByChargeId, containsInAnyOrder(
                allOf(
                        org.hamcrest.Matchers.hasEntry("external_id", firstRefundId),
                        org.hamcrest.Matchers.hasEntry("amount", (Object) firstRefundAmount),
                        org.hamcrest.Matchers.hasEntry("status", RefundStatus.REFUND_SUBMITTED.getValue()),
                        org.hamcrest.Matchers.hasEntry("charge_id", (Object) defaultTestCharge.getChargeId())
                ),
                allOf(
                        org.hamcrest.Matchers.hasEntry("external_id", secondRefundId),
                        org.hamcrest.Matchers.hasEntry("amount", (Object) secondRefundAmount),
                        org.hamcrest.Matchers.hasEntry("status", RefundStatus.REFUND_SUBMITTED.getValue()),
                        org.hamcrest.Matchers.hasEntry("charge_id", (Object) defaultTestCharge.getChargeId())
                )));
    }

    @Test
    public void shouldFailRequestingARefund_whenAmountIsBiggerThanChargeAmount() {
        Long refundAmount = defaultTestCharge.getAmount() + 20;

        postRefundFor(refundAmount)
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("message", is(format("Charge with id [%s] not available for refund.", defaultTestCharge.getExternalChargeId())));

        java.util.List<Map<String, Object>> refundsFoundByChargeId = databaseTestHelper.getRefundsByChargeId(defaultTestCharge.getChargeId());
        assertThat(refundsFoundByChargeId.size(), is(0));
    }

    @Test
    public void shouldFailRequestingARefund_whenAPartialRefundMakesTotalRefundedAmountBiggerThanChargeAmount() {
        Long firstRefundAmount = 80L;
        Long secondRefundAmount = 30L; // 10 more than available

        ValidatableResponse validatableResponse = postRefundFor(firstRefundAmount);
        String firstRefundId = assertRefundResponseWith(firstRefundAmount, validatableResponse, ACCEPTED.getStatusCode());

        assertRefundDatabaseRecord(firstRefundAmount, firstRefundId);

        postRefundFor(secondRefundAmount)
                .statusCode(400)
                .body("message", is(format("Charge with id [%s] not available for refund.", defaultTestCharge.getExternalChargeId())));

        assertRefundDatabaseRecord(firstRefundAmount, firstRefundId);
    }

    @Test
    public void shouldBeAbleRetrieveARefund() {
        DatabaseFixtures.TestRefund testRefund = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withTestCharge(defaultTestCharge)
                .withType(RefundStatus.REFUND_SUBMITTED)
                .insert();

        ValidatableResponse validatableResponse = getRefundFor(
                defaultTestAccount.getAccountId(), defaultTestCharge.getExternalChargeId(), testRefund.getExternalRefundId());
        String refundId = assertRefundResponseWith(testRefund.getAmount(), validatableResponse, OK.getStatusCode());

        assertRefundDatabaseRecord(testRefund.getAmount(), refundId);
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

    private ValidatableResponse postRefundFor(Long refundAmount) {
        worldpay.mockRefundResponse();
        return givenSetup()
                .body("{\"amount\": " + refundAmount + "}")
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .post(REFUNDS_API_PATH
                        .replace("{accountId}", accountId)
                        .replace("{chargeId}", defaultTestCharge.getExternalChargeId()))
                .then();
    }

    private ValidatableResponse getRefundFor(Long accountId, String chargeId, String refundId) {
        worldpay.mockRefundResponse();
        return givenSetup()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .get(REFUND_API_PATH
                        .replace("{accountId}", accountId.toString())
                        .replace("{chargeId}", chargeId)
                        .replace("{refundId}", refundId))
                .then();
    }

    private String assertRefundResponseWith(Long refundAmount, ValidatableResponse validatableResponse, int expectedStatusCode) {
        ValidatableResponse response = validatableResponse
                .statusCode(expectedStatusCode)
                .body("refund_id", is(notNullValue()))
                .body("amount", is(refundAmount.intValue()))
                .body("status", is("submitted"))
                .body("created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3}Z"))
                .body("created_date", isWithin(10, SECONDS));

        String paymentUrl = format("http://localhost:%s/v1/api/accounts/%s/charges/%s",
                app.getLocalPort(), defaultTestAccount.getAccountId(), defaultTestCharge.getExternalChargeId());

        String refundId = response.extract().path("refund_id");
        response.body("_links.self.href", is(paymentUrl + "/refunds/" + refundId))
                .body("_links.payment.href", is(paymentUrl));

        return refundId;
    }

    private void assertRefundDatabaseRecord(Long refundAmount, String refundId) {
        java.util.List<Map<String, Object>> refundsFoundByChargeId = databaseTestHelper.getRefundsByChargeId(defaultTestCharge.getChargeId());

        assertThat(refundsFoundByChargeId.size(), is(1));
        assertThat(refundsFoundByChargeId, containsInAnyOrder(
                allOf(
                        org.hamcrest.Matchers.hasEntry("external_id", refundId),
                        org.hamcrest.Matchers.hasEntry("amount", (Object)refundAmount),
                        org.hamcrest.Matchers.hasEntry("status", RefundStatus.REFUND_SUBMITTED.getValue()),
                        org.hamcrest.Matchers.hasEntry("charge_id", (Object) defaultTestCharge.getChargeId())
                )));
    }
}
