package uk.gov.pay.connector.it.resources.sandbox;

import com.google.gson.Gson;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.common.model.api.ExternalRefundStatus;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.time.temporal.ChronoUnit.SECONDS;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.PRECONDITION_FAILED;
import static org.eclipse.jetty.http.HttpStatus.FORBIDDEN_403;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.matcher.RefundsMatcher.aRefundMatching;
import static uk.gov.pay.connector.matcher.ZoneDateTimeAsStringWithinMatcher.isWithin;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.aValidLedgerTransaction;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userEmail;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userExternalId;

public class SandboxRefundsResourceIT  {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("sandbox", app.getLocalPort(), app.getDatabaseTestHelper());

    private DatabaseFixtures.TestAccount defaultTestAccount;
    private DatabaseFixtures.TestCharge defaultTestCharge;
    
    @BeforeEach
    void setUpCharge() {
        defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestAccount()
                .withAccountId(Long.parseLong(testBaseExtension.getAccountId()))
                .withPaymentProvider(testBaseExtension.getPaymentProvider())
                .withGatewayAccountCredentials(List.of(testBaseExtension.getCredentialParams()))
                .withCredentials(testBaseExtension.getCredentials());

        defaultTestCharge = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withAmount(100L)
                .withTestAccount(defaultTestAccount)
                .withChargeStatus(CAPTURED)
                .withPaymentProvider(testBaseExtension.getPaymentProvider())
                .withGatewayCredentialId(testBaseExtension.getCredentialParams().getId())
                .insert();
    }

    @Test
    void shouldRespond_403_WhenGatewayAccountDisabled() {
        Long refundAmount = 50L;

        app.getDatabaseTestHelper().setDisabled(defaultTestAccount.getAccountId());

        postRefundFor(defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount())
                .statusCode(FORBIDDEN_403)
                .body("message", contains("This gateway account is disabled"))
                .body("error_identifier", is(ErrorIdentifier.ACCOUNT_DISABLED.toString()));
    }
    
    @Test
    void shouldBeAbleToRequestARefund_partialAmount() {
        Long refundAmount = 50L;
        ValidatableResponse validatableResponse = postRefundFor(defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount());

        String refundId = assertRefundResponseWith(refundAmount, validatableResponse, ACCEPTED.getStatusCode());

        List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
        assertThat(refundsFoundByChargeExternalId.size(), is(1));
        assertThat(refundsFoundByChargeExternalId, hasItems(aRefundMatching(refundId, is(notNullValue()), defaultTestCharge.getExternalChargeId(), refundAmount, "REFUNDED")));

        assertRefundsHistoryInOrderInDBForSuccessfulOrPartialRefund(defaultTestCharge);
    }

    @Test
    void shouldBeAbleToRefundTwoRequestsWhereAmountAvailableMatch() {
        Long refundAmount = 50L;
        //first refund request
        ValidatableResponse validatableResponse = postRefundFor(defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount());
        String refundId = assertRefundResponseWith(refundAmount, validatableResponse, ACCEPTED.getStatusCode());

        //second refund request with updated refundAmountAvailable
        validatableResponse = postRefundFor(defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount() - refundAmount);
        String refundId_2 = assertRefundResponseWith(refundAmount, validatableResponse, ACCEPTED.getStatusCode());

        List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
        assertThat(refundsFoundByChargeExternalId.size(), is(2));
        assertThat(refundsFoundByChargeExternalId, hasItems(aRefundMatching(refundId, is(notNullValue()), defaultTestCharge.getExternalChargeId(), refundAmount, "REFUNDED")));
        assertThat(refundsFoundByChargeExternalId, hasItems(aRefundMatching(refundId_2, is(notNullValue()), defaultTestCharge.getExternalChargeId(), refundAmount, "REFUNDED")));

        assertRefundsHistoryInOrderInDBForTwoRefunds(defaultTestCharge);
    }

    @Test
    void shouldRespond_412_WhenSecondRefundRequestAmountAvailableMismatches() {
        Long refundAmount = 50L;
        //first refund request
        ValidatableResponse validatableResponse = postRefundFor(defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount());
        String refundId = assertRefundResponseWith(refundAmount, validatableResponse, ACCEPTED.getStatusCode());

        //second refund request with wrong refundAmountAvailable
        postRefundFor(defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount())
                .statusCode(PRECONDITION_FAILED.getStatusCode())
                .body("message", contains("Refund Amount Available Mismatch"))
                .body("error_identifier", is(ErrorIdentifier.REFUND_AMOUNT_AVAILABLE_MISMATCH.toString()));

        List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
        assertThat(refundsFoundByChargeExternalId.size(), is(1));
        assertThat(refundsFoundByChargeExternalId, hasItems(aRefundMatching(refundId, is(notNullValue()), defaultTestCharge.getExternalChargeId(), refundAmount, "REFUNDED")));

        assertRefundsHistoryInOrderInDBForSuccessfulOrPartialRefund(defaultTestCharge);
    }

    @Test
    void shouldBeAbleToRequestARefund_fullAmount() {

        Long refundAmount = defaultTestCharge.getAmount();

        ValidatableResponse validatableResponse = postRefundFor(defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount(),
                userExternalId, userEmail);
        String refundId = assertRefundResponseWith(refundAmount, validatableResponse, ACCEPTED.getStatusCode());

        List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
        assertThat(refundsFoundByChargeExternalId.size(), is(1));
        assertThat(refundsFoundByChargeExternalId, hasItems(aRefundMatching(refundId, is(notNullValue()), defaultTestCharge.getExternalChargeId(), refundAmount, "REFUNDED")));
        assertThat(refundsFoundByChargeExternalId.get(0), hasEntry("user_external_id", userExternalId));
        assertThat(refundsFoundByChargeExternalId.get(0), hasEntry("user_email", userEmail));
        assertThat(refundsFoundByChargeExternalId.get(0), hasEntry("charge_external_id", defaultTestCharge.getExternalChargeId()));

        assertRefundsHistoryInOrderInDBForSuccessfulOrPartialRefund(defaultTestCharge);
    }

    @Test
    void shouldBeAbleToRequestARefund_multiplePartialAmounts_andRefundShouldBeInFullStatus() {
        Long firstRefundAmount = 80L;
        Long secondRefundAmount = 20L;
        String externalChargeId = defaultTestCharge.getExternalChargeId();

        ValidatableResponse firstValidatableResponse = postRefundFor(externalChargeId, firstRefundAmount, defaultTestCharge.getAmount());
        String firstRefundId = assertRefundResponseWith(firstRefundAmount, firstValidatableResponse, ACCEPTED.getStatusCode());

        ValidatableResponse secondValidatableResponse = postRefundFor(externalChargeId, secondRefundAmount, defaultTestCharge.getAmount() - firstRefundAmount);
        String secondRefundId = assertRefundResponseWith(secondRefundAmount, secondValidatableResponse, ACCEPTED.getStatusCode());

        List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(externalChargeId);
        assertThat(refundsFoundByChargeExternalId.size(), is(2));

        assertThat(refundsFoundByChargeExternalId, hasItems(
                aRefundMatching(secondRefundId, is(notNullValue()), externalChargeId, secondRefundAmount, "REFUNDED"),
                aRefundMatching(firstRefundId, is(notNullValue()), externalChargeId, firstRefundAmount, "REFUNDED")));

        assertRefundsHistoryInOrderInDBForTwoRefunds(defaultTestCharge);

        testBaseExtension.getConnectorRestApiClient().withChargeId(externalChargeId)
                .getCharge()
                .statusCode(200)
                .body("refund_summary.status", is("full"))
                .body("refund_summary.amount_available", is(0))
                .body("refund_summary.amount_submitted", is(100));
    }

    @Test
    void shouldFailRequestingARefund_whenChargeStatusMakesItNotRefundable() {

        DatabaseFixtures.TestCharge testCharge = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withAmount(100L)
                .withTestAccount(defaultTestAccount)
                .withChargeStatus(ENTERING_CARD_DETAILS)
                .insert();

        Long refundAmount = 20L;

        postRefundFor(testCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount())
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("reason", is("pending"))
                .body("message", contains(format("Charge with id [%s] not available for refund.", testCharge.getExternalChargeId())))
                .body("error_identifier", is(ErrorIdentifier.REFUND_NOT_AVAILABLE.toString()));

        List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
        assertThat(refundsFoundByChargeExternalId.size(), is(0));
    }

    @Test
    void shouldFailRequestingARefund_whenChargeRefundIsFull() {

        Long refundAmount = defaultTestCharge.getAmount();
        String externalChargeId = defaultTestCharge.getExternalChargeId();

        postRefundFor(externalChargeId, refundAmount, defaultTestCharge.getAmount())
                .statusCode(ACCEPTED.getStatusCode());

        List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(externalChargeId);
        assertThat(refundsFoundByChargeExternalId.size(), is(1));

        postRefundFor(externalChargeId, 1L, defaultTestCharge.getAmount() - refundAmount)
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("reason", is("full"))
                .body("message", contains(format("Charge with id [%s] not available for refund.", externalChargeId)))
                .body("error_identifier", is(ErrorIdentifier.REFUND_NOT_AVAILABLE.toString()));
    }

    @Test
    void shouldFailRequestingARefund_whenAmountIsBiggerThanChargeAmount() {
        Long refundAmount = defaultTestCharge.getAmount() + 20;

        postRefundFor(defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount())
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("reason", is("amount_not_available"))
                .body("message", contains("Not sufficient amount available for refund"))
                .body("error_identifier", is(ErrorIdentifier.REFUND_NOT_AVAILABLE.toString()));

        List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
        assertThat(refundsFoundByChargeExternalId.size(), is(0));

        List<String> refundsHistory = app.getDatabaseTestHelper().getRefundsHistoryByChargeExternalId(defaultTestCharge.getExternalChargeId()).stream().map(x -> x.get("status").toString()).collect(Collectors.toList());
        assertThat(refundsHistory.size(), is(0));
    }

    @Test
    void shouldFailRequestingARefund_whenAmountIsBiggerThanAllowedChargeAmount() {

        Long refundAmount = 10000001L;

        postRefundFor(defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount())
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("reason", is("amount_not_available"))
                .body("message", contains("Not sufficient amount available for refund"))
                .body("error_identifier", is(ErrorIdentifier.REFUND_NOT_AVAILABLE.toString()));

        List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
        assertThat(refundsFoundByChargeExternalId.size(), is(0));

        List<String> refundsHistory = app.getDatabaseTestHelper().getRefundsHistoryByChargeExternalId(defaultTestCharge.getExternalChargeId()).stream().map(x -> x.get("status").toString()).collect(Collectors.toList());
        assertThat(refundsHistory.size(), is(0));
    }

    @Test
    void shouldFailRequestingARefund_whenAmountIsLessThanOnePence() {

        Long refundAmount = 0L;

        postRefundFor(defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount())
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("reason", is("amount_min_validation"))
                .body("message", contains("Validation error for amount. Minimum amount for a refund is 1"))
                .body("error_identifier", is(ErrorIdentifier.REFUND_NOT_AVAILABLE.toString()));

        List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
        assertThat(refundsFoundByChargeExternalId.size(), is(0));

        List<String> refundsHistory = app.getDatabaseTestHelper().getRefundsHistoryByChargeExternalId(defaultTestCharge.getExternalChargeId()).stream().map(x -> x.get("status").toString()).collect(Collectors.toList());
        assertThat(refundsHistory.size(), is(0));
    }

    @Test
    void shouldFailRequestingARefund_whenAPartialRefundMakesTotalRefundedAmountBiggerThanChargeAmount() {
        Long firstRefundAmount = 80L;
        Long secondRefundAmount = 30L; // 10 more than available

        ValidatableResponse validatableResponse = postRefundFor(defaultTestCharge.getExternalChargeId(), firstRefundAmount, defaultTestCharge.getAmount());
        String firstRefundId = assertRefundResponseWith(firstRefundAmount, validatableResponse, ACCEPTED.getStatusCode());

        List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
        assertThat(refundsFoundByChargeExternalId.size(), is(1));
        assertThat(refundsFoundByChargeExternalId, hasItems(aRefundMatching(firstRefundId, is(notNullValue()), defaultTestCharge.getExternalChargeId(), firstRefundAmount, "REFUNDED")));

        postRefundFor(defaultTestCharge.getExternalChargeId(), secondRefundAmount, defaultTestCharge.getAmount() - firstRefundAmount)
                .statusCode(400)
                .body("reason", is("amount_not_available"))
                .body("message", contains("Not sufficient amount available for refund"))
                .body("error_identifier", is(ErrorIdentifier.REFUND_NOT_AVAILABLE.toString()));

        List<Map<String, Object>> refundsFoundByChargeExternalId1 = app.getDatabaseTestHelper().getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
        assertThat(refundsFoundByChargeExternalId1.size(), is(1));
        assertThat(refundsFoundByChargeExternalId1, hasItems(aRefundMatching(firstRefundId, is(notNullValue()), defaultTestCharge.getExternalChargeId(), firstRefundAmount, "REFUNDED")));

        assertRefundsHistoryInOrderInDBForSuccessfulOrPartialRefund(defaultTestCharge);
    }

    @Test
    void shouldFailRequestingARefundForHistoricCharge_whenPartialRefundAmountGreaterThanRemainingAmount_whenExistingPartialRefundsHaveBeenExpunged() throws Exception {
        String chargeExternalId = "historic-charge-id";
        
        ChargeResponse.RefundSummary refundSummary = new ChargeResponse.RefundSummary();
        refundSummary.setStatus("available");
        LedgerTransaction charge = aValidLedgerTransaction()
                .withExternalId(chargeExternalId)
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withCredentialExternalId(testBaseExtension.getCredentialParams().getExternalId())
                .withAmount(1000L)
                .withRefundSummary(refundSummary)
                .withPaymentProvider(testBaseExtension.getPaymentProvider())
                .build();
        app.getLedgerStub().returnLedgerTransaction(chargeExternalId, charge);

        // add one refund that is still in connector and another that is only in ledger to check
        // that both are used when calculating refundability
        app.getDatabaseTestHelper().addRefund("connector-refund-id", 500L, RefundStatus.CREATED, "refund-gateway-id-1", ZonedDateTime.now(), chargeExternalId);

        LedgerTransaction expungedRefund = aValidLedgerTransaction()
                .withExternalId("ledger-refund-id")
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withParentTransactionId(defaultTestCharge.getExternalChargeId())
                .withCredentialExternalId(testBaseExtension.getCredentialParams().getExternalId())
                .withAmount(300L)
                .withStatus(ExternalRefundStatus.EXTERNAL_SUCCESS.getStatus())
                .withPaymentProvider(testBaseExtension.getPaymentProvider())
                .build();
        app.getLedgerStub().returnRefundsForPayment(chargeExternalId, List.of(expungedRefund));

        Long refundAmount = 201L;
        postRefundFor(chargeExternalId, refundAmount, 200L)
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("reason", is("amount_not_available"))
                .body("message", contains("Not sufficient amount available for refund"))
                .body("error_identifier", is(ErrorIdentifier.REFUND_NOT_AVAILABLE.toString()));
    }

    @Test
    void shouldSucceedRequestingARefundForHistoricCharge_whenExistingPartialRefundsHaveBeenExpunged() throws Exception {
        String chargeExternalId = "historic-charge-id";

        ChargeResponse.RefundSummary refundSummary = new ChargeResponse.RefundSummary();
        refundSummary.setStatus("available");
        LedgerTransaction charge = aValidLedgerTransaction()
                .withExternalId(chargeExternalId)
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withCredentialExternalId(testBaseExtension.getCredentialParams().getExternalId())
                .withAmount(1000L)
                .withRefundSummary(refundSummary)
                .withPaymentProvider(testBaseExtension.getPaymentProvider())
                .build();
        app.getLedgerStub().returnLedgerTransaction(chargeExternalId, charge);

        // add one refund that is still in connector and another that is only in ledger to check
        // that both are used when calculating refundability
        app.getDatabaseTestHelper().addRefund("connector-refund-id", 500L, RefundStatus.CREATED, "refund-gateway-id-1", ZonedDateTime.now(), chargeExternalId);

        LedgerTransaction expungedRefund = aValidLedgerTransaction()
                .withExternalId("ledger-refund-id")
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withCredentialExternalId(testBaseExtension.getCredentialParams().getExternalId())
                .withParentTransactionId(defaultTestCharge.getExternalChargeId())
                .withAmount(300L)
                .withStatus(ExternalRefundStatus.EXTERNAL_SUCCESS.getStatus())
                .withPaymentProvider(testBaseExtension.getPaymentProvider())
                .build();
        app.getLedgerStub().returnRefundsForPayment(chargeExternalId, List.of(expungedRefund));

        Long refundAmount = 200L;
        postRefundFor(chargeExternalId, refundAmount, 200L)
                .statusCode(ACCEPTED.getStatusCode());
    }

    @Test
    void shouldErrorRequestingARefundForHistoricCharge_whenErrorReturnedByLedger() throws Exception {
        String chargeExternalId = "historic-charge-id";

        ChargeResponse.RefundSummary refundSummary = new ChargeResponse.RefundSummary();
        refundSummary.setStatus("available");
        LedgerTransaction charge = aValidLedgerTransaction()
                .withExternalId(chargeExternalId)
                .withGatewayAccountId(defaultTestAccount.getAccountId())
                .withCredentialExternalId(testBaseExtension.getCredentialParams().getExternalId())
                .withAmount(1000L)
                .withRefundSummary(refundSummary)
                .build();
        app.getLedgerStub().returnLedgerTransaction(chargeExternalId, charge);

        app.getLedgerStub().returnErrorForFindRefundsForPayment(chargeExternalId);

        postRefundFor(chargeExternalId, 200L, 200L)
                .statusCode(INTERNAL_SERVER_ERROR.getStatusCode());

        List<Map<String, Object>> refunds = app.getDatabaseTestHelper().getRefundsByChargeExternalId(chargeExternalId);
        assertThat(refunds, hasSize(0));
    }

    private ValidatableResponse postRefundFor(String chargeId, Long refundAmount, long refundAmountAvlbl) {
        return postRefundFor(chargeId, refundAmount, refundAmountAvlbl, null, null);
    }

    private ValidatableResponse postRefundFor(String chargeId, Long refundAmount, long refundAmountAvlbl,
                                              String userExternalId, String userEmail) {
        Map<String, Object> refundData = new HashMap();

        refundData.put("amount", refundAmount);
        refundData.put("refund_amount_available", refundAmountAvlbl);

        if (userExternalId != null && userEmail != null) {
            refundData.put("user_external_id", userExternalId);
            refundData.put("user_email", userEmail);
        }

        String refundPayload = new Gson().toJson(refundData);

        return app.givenSetup()
                .body(refundPayload)
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .post("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds"
                        .replace("{accountId}", testBaseExtension.getAccountId())
                        .replace("{chargeId}", chargeId))
                .then();
    }

    private String assertRefundResponseWith(Long refundAmount, ValidatableResponse validatableResponse, int expectedStatusCode) {
        ValidatableResponse response = validatableResponse
                .statusCode(expectedStatusCode)
                .body("refund_id", is(notNullValue()))
                .body("amount", is(refundAmount.intValue()))
                .body("status", is("success"))
                .body("created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(.\\d{1,3})?Z"))
                .body("created_date", isWithin(10, SECONDS));

        String paymentUrl = format("https://localhost:%s/v1/api/accounts/%s/charges/%s",
                app.getLocalPort(), defaultTestAccount.getAccountId(), defaultTestCharge.getExternalChargeId());

        String refundId = response.extract().path("refund_id");
        response.body("_links.self.href", is(paymentUrl + "/refunds/" + refundId))
                .body("_links.payment.href", is(paymentUrl));

        return refundId;
    }

    private void assertRefundsHistoryInOrderInDBForSuccessfulOrPartialRefund(DatabaseFixtures.TestCharge defaultTestCharge) {
        List<String> refundsHistory = app.getDatabaseTestHelper().getRefundsHistoryByChargeExternalId(defaultTestCharge.getExternalChargeId()).stream().map(x -> x.get("status").toString()).collect(Collectors.toList());
        assertThat(refundsHistory.size(), is(3));
        assertThat(refundsHistory, contains("REFUNDED", "REFUND SUBMITTED", "CREATED"));
    }

    private void assertRefundsHistoryInOrderInDBForTwoRefunds(DatabaseFixtures.TestCharge defaultTestCharge) {
        List<String> refundsHistory = app.getDatabaseTestHelper().getRefundsHistoryByChargeExternalId(defaultTestCharge.getExternalChargeId()).stream().map(x -> x.get("status").toString()).collect(Collectors.toList());
        assertThat(refundsHistory.size(), is(6));
        assertThat(refundsHistory, contains("REFUNDED", "REFUND SUBMITTED", "CREATED", "REFUNDED", "REFUND SUBMITTED", "CREATED"));
    }
}
