package uk.gov.pay.connector.it.resources.sandbox;

import com.google.gson.Gson;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.common.model.api.ExternalRefundStatus;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static jakarta.ws.rs.core.Response.Status.ACCEPTED;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static jakarta.ws.rs.core.Response.Status.PRECONDITION_FAILED;
import static java.lang.String.format;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.eclipse.jetty.http.HttpStatus.FORBIDDEN_403;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
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
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_IN_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_OUT_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.matcher.RefundsMatcher.aRefundMatching;
import static uk.gov.pay.connector.matcher.ZoneDateTimeAsStringWithinMatcher.isWithin;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.aValidLedgerTransaction;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userEmail;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userExternalId;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomLong;

public class SandboxRefundsResourceIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    private final String PAYMENT_PROVIDER = "sandbox";

    private final Long accountId = randomLong();
    private final String SERVICE_ID = "a-valid-service-id";
    private DatabaseFixtures.TestAccount defaultTestAccount;
    private DatabaseFixtures.TestCharge defaultTestCharge;
    private final Map<String, Object> sandboxCredentials = Map.of(
            CREDENTIALS_MERCHANT_ID, "merchant-id",
            CREDENTIALS_USERNAME, "test-user",
            CREDENTIALS_PASSWORD, "test-password",
            CREDENTIALS_SHA_IN_PASSPHRASE, "test-sha-in-passphrase",
            CREDENTIALS_SHA_OUT_PASSPHRASE, "test-sha-out-passphrase"
    );

    @BeforeEach
    void setUpCharge() {
        defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestAccount()
                .withAccountId(accountId)
                .withServiceId(SERVICE_ID)
                .withPaymentProvider(PAYMENT_PROVIDER)
                .withCredentials(sandboxCredentials)
                .insert();

        defaultTestCharge = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withAmount(100L)
                .withTestAccount(defaultTestAccount)
                .withChargeStatus(CAPTURED)
                .withPaymentProvider(PAYMENT_PROVIDER)
                .insert();
    }

    @Nested
    class ByAccountId {
        @Nested
        class SubmitRefund {
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
                assertThat(refundsFoundByChargeExternalId.getFirst(), hasEntry("user_external_id", userExternalId));
                assertThat(refundsFoundByChargeExternalId.getFirst(), hasEntry("user_email", userEmail));
                assertThat(refundsFoundByChargeExternalId.getFirst(), hasEntry("charge_external_id", defaultTestCharge.getExternalChargeId()));

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

                app.givenSetup()
                        .get(format("/v1/api/accounts/%s/charges/%s/", accountId, defaultTestCharge.getExternalChargeId()))
                        .then()
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
                        .withAmount(1000L)
                        .withRefundSummary(refundSummary)
                        .withPaymentProvider(PAYMENT_PROVIDER)
                        .build();
                app.getLedgerStub().returnLedgerTransaction(chargeExternalId, defaultTestAccount.getAccountId(), charge);

                // add one refund that is still in connector and another that is only in ledger to check
                // that both are used when calculating refundability
                app.getDatabaseTestHelper().addRefund("connector-refund-id", 500L, RefundStatus.CREATED, "refund-gateway-id-1", ZonedDateTime.now(), chargeExternalId);

                LedgerTransaction expungedRefund = aValidLedgerTransaction()
                        .withExternalId("ledger-refund-id")
                        .withGatewayAccountId(defaultTestAccount.getAccountId())
                        .withParentTransactionId(chargeExternalId)
                        .withAmount(300L)
                        .withStatus(ExternalRefundStatus.EXTERNAL_SUCCESS.getStatus())
                        .withPaymentProvider(PAYMENT_PROVIDER)
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
                        .withAmount(1000L)
                        .withRefundSummary(refundSummary)
                        .withPaymentProvider(PAYMENT_PROVIDER)
                        .build();
                app.getLedgerStub().returnLedgerTransaction(chargeExternalId, defaultTestAccount.getAccountId(), charge);

                // add one refund that is still in connector and another that is only in ledger to check
                // that both are used when calculating refundability
                app.getDatabaseTestHelper().addRefund("connector-refund-id", 500L, RefundStatus.CREATED, "refund-gateway-id-1", ZonedDateTime.now(), chargeExternalId);

                LedgerTransaction expungedRefund = aValidLedgerTransaction()
                        .withExternalId("ledger-refund-id")
                        .withGatewayAccountId(defaultTestAccount.getAccountId())
                        .withParentTransactionId(defaultTestCharge.getExternalChargeId())
                        .withAmount(300L)
                        .withStatus(ExternalRefundStatus.EXTERNAL_SUCCESS.getStatus())
                        .withPaymentProvider(PAYMENT_PROVIDER)
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
                        .withAmount(1000L)
                        .withRefundSummary(refundSummary)
                        .build();
                app.getLedgerStub().returnLedgerTransaction(chargeExternalId, defaultTestAccount.getAccountId(), charge);

                app.getLedgerStub().returnErrorForFindRefundsForPayment(chargeExternalId);

                postRefundFor(chargeExternalId, 200L, 200L)
                        .statusCode(INTERNAL_SERVER_ERROR.getStatusCode());

                List<Map<String, Object>> refunds = app.getDatabaseTestHelper().getRefundsByChargeExternalId(chargeExternalId);
                assertThat(refunds, hasSize(0));
            }
        }
    }

    @Nested
    class ByServiceIdAndType {
        @Nested
        class SubmitRefund {
            @Test
            @DisplayName("Should return 403 when gateway account is disabled")
            void shouldRespond_404_WhenGatewayAccountDisabled() {
                int refundAmount = 50;
                app.getDatabaseTestHelper().setDisabled(defaultTestAccount.getAccountId());

                app.givenSetup()
                        .body(toJson(Map.of(
                                "amount", refundAmount,
                                "refund_amount_available", defaultTestCharge.getAmount()

                        )))
                        .post(format("/v1/api/service/%s/account/%s/charges/%s/refunds", SERVICE_ID, TEST, defaultTestCharge.getExternalChargeId()))
                        .then()
                        .statusCode(NOT_FOUND_404)
                        .body("message", contains(format("Gateway account not found for service external id [%s] and account type [test]", SERVICE_ID)))
                        .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
            }

            @Test
            @DisplayName("Should create refund entity in database and update refund history for partial refund")
            void shouldBeAbleToRequestARefund_partialAmount() {
                int refundAmount = 50;

                // Attempt to submit refund
                var response = app.givenSetup()
                        .body(toJson(Map.of(
                                "amount", refundAmount,
                                "refund_amount_available", defaultTestCharge.getAmount()

                        )))
                        .post(format("/v1/api/service/%s/account/%s/charges/%s/refunds", SERVICE_ID, TEST, defaultTestCharge.getExternalChargeId()))
                        .then()
                        .statusCode(ACCEPTED.getStatusCode())
                        .body("refund_id", is(notNullValue()))
                        .body("amount", is(refundAmount))
                        .body("status", is("success"))
                        .body("created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(.\\d{1,3})?Z"))
                        .body("created_date", isWithin(10, SECONDS));

                // Verify payment links in response
                String paymentUrl = format("https://localhost:%s/v1/api/service/%s/account/%s/charges/%s",
                        app.getLocalPort(), SERVICE_ID, TEST, defaultTestCharge.getExternalChargeId());
                String refundId = response.extract().path("refund_id");
                response.body("_links.self.href", is(paymentUrl + "/refunds/" + refundId))
                        .body("_links.payment.href", is(paymentUrl));

                // Verify refund entity created in database
                List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
                assertThat(refundsFoundByChargeExternalId.size(), is(1));
                assertThat(refundsFoundByChargeExternalId, hasItems(aRefundMatching(refundId, is(notNullValue()), defaultTestCharge.getExternalChargeId(), refundAmount, "REFUNDED")));

                // Verify refund history
                List<String> refundsHistory = app.getDatabaseTestHelper().getRefundStatusHistoryByChargeExternalId(defaultTestCharge.getExternalChargeId());
                assertThat(refundsHistory.size(), is(3));
                assertThat(refundsHistory, contains("REFUNDED", "REFUND SUBMITTED", "CREATED"));
            }


            @Test
            @DisplayName("Should create refund entity in database and update refund history for full refund")
            void shouldBeAbleToRequestARefund_fullAmount() {
                int refundAmount = (int) defaultTestCharge.getAmount(); // JSON numbers are parsed as int if < INTEGER_MAX

                // Attempt to submit refund
                var response = app.givenSetup()
                        .body(toJson(Map.of(
                                "amount", refundAmount,
                                "refund_amount_available", defaultTestCharge.getAmount(),
                                "user_external_id", userExternalId,
                                "user_email", userEmail
                        )))
                        .post(format("/v1/api/service/%s/account/%s/charges/%s/refunds", SERVICE_ID, TEST, defaultTestCharge.getExternalChargeId()))
                        .then()
                        .statusCode(ACCEPTED.getStatusCode())
                        .body("refund_id", is(notNullValue()))
                        .body("amount", is(refundAmount))
                        .body("status", is("success"))
                        .body("created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(.\\d{1,3})?Z"))
                        .body("created_date", isWithin(10, SECONDS));

                // Verify payment links in response
                String paymentUrl = format("https://localhost:%s/v1/api/service/%s/account/%s/charges/%s",
                        app.getLocalPort(), SERVICE_ID, TEST, defaultTestCharge.getExternalChargeId());
                String refundId = response.extract().path("refund_id");
                response.body("_links.self.href", is(paymentUrl + "/refunds/" + refundId))
                        .body("_links.payment.href", is(paymentUrl));

                // Verify refund entity created in database
                List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
                assertThat(refundsFoundByChargeExternalId.size(), is(1));
                assertThat(refundsFoundByChargeExternalId, hasItems(aRefundMatching(refundId, is(notNullValue()), defaultTestCharge.getExternalChargeId(), refundAmount, "REFUNDED")));
                assertThat(refundsFoundByChargeExternalId.getFirst(), hasEntry("user_external_id", userExternalId));
                assertThat(refundsFoundByChargeExternalId.getFirst(), hasEntry("user_email", userEmail));
                assertThat(refundsFoundByChargeExternalId.getFirst(), hasEntry("charge_external_id", defaultTestCharge.getExternalChargeId()));

                // Verify refund history
                List<String> refundsHistory = app.getDatabaseTestHelper().getRefundStatusHistoryByChargeExternalId(defaultTestCharge.getExternalChargeId());
                assertThat(refundsHistory.size(), is(3));
                assertThat(refundsHistory, contains("REFUNDED", "REFUND SUBMITTED", "CREATED"));
            }

            @Test
            @DisplayName("Should create refund two refund entities with correct status history when submitting two refunds with correct amounts available")
            void shouldBeAbleToRefundTwoRequestsWhereAmountAvailableMatch() {
                int refundAmount = 50;

                // Attempt to submit first refund
                String firstRefundId = app.givenSetup()
                        .body(toJson(Map.of(
                                "amount", refundAmount,
                                "refund_amount_available", defaultTestCharge.getAmount()

                        )))
                        .post(format("/v1/api/service/%s/account/%s/charges/%s/refunds", SERVICE_ID, TEST, defaultTestCharge.getExternalChargeId()))
                        .then()
                        .statusCode(ACCEPTED.getStatusCode())
                        .extract().path("refund_id");

                // Attempt to submit second request
                String secondRefundId = app.givenSetup()
                        .body(toJson(Map.of(
                                "amount", refundAmount,
                                "refund_amount_available", defaultTestCharge.getAmount() - refundAmount

                        )))
                        .post(format("/v1/api/service/%s/account/%s/charges/%s/refunds", SERVICE_ID, TEST, defaultTestCharge.getExternalChargeId()))
                        .then()
                        .statusCode(ACCEPTED.getStatusCode())
                        .extract().path("refund_id");

                List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
                assertThat(refundsFoundByChargeExternalId.size(), is(2));
                assertThat(refundsFoundByChargeExternalId, hasItems(aRefundMatching(firstRefundId, is(notNullValue()), defaultTestCharge.getExternalChargeId(), refundAmount, "REFUNDED")));
                assertThat(refundsFoundByChargeExternalId, hasItems(aRefundMatching(secondRefundId, is(notNullValue()), defaultTestCharge.getExternalChargeId(), refundAmount, "REFUNDED")));


                // Verify refund history
                List<String> firstRefundsHistory = app.getDatabaseTestHelper().getRefundsHistoryByRefundExternalId(firstRefundId).stream().map(x -> x.get("status").toString()).collect(Collectors.toList());
                ;
                assertThat(firstRefundsHistory.size(), is(3));
                assertThat(firstRefundsHistory, contains("REFUNDED", "REFUND SUBMITTED", "CREATED"));

                List<String> secondRefundsHistory = app.getDatabaseTestHelper().getRefundsHistoryByRefundExternalId(secondRefundId).stream().map(x -> x.get("status").toString()).collect(Collectors.toList());
                ;
                assertThat(secondRefundsHistory.size(), is(3));
                assertThat(secondRefundsHistory, contains("REFUNDED", "REFUND SUBMITTED", "CREATED"));
            }

            @Test
            @DisplayName("Should show refund status as full when multiple partial refunds are submitted adding up to full charge amount")
            void shouldBeAbleToRequestARefund_multiplePartialAmounts_andRefundShouldBeInFullStatus() {
                int firstRefundAmount = 80;
                int secondRefundAmount = 20;

                // Attempt to submit first refund
                app.givenSetup()
                        .body(toJson(Map.of(
                                "amount", firstRefundAmount,
                                "refund_amount_available", defaultTestCharge.getAmount()

                        )))
                        .post(format("/v1/api/service/%s/account/%s/charges/%s/refunds", SERVICE_ID, TEST, defaultTestCharge.getExternalChargeId()))
                        .then().statusCode(ACCEPTED.getStatusCode());

                // Attempt to submit second refund
                app.givenSetup()
                        .body(toJson(Map.of(
                                "amount", secondRefundAmount,
                                "refund_amount_available", defaultTestCharge.getAmount() - firstRefundAmount

                        )))
                        .post(format("/v1/api/service/%s/account/%s/charges/%s/refunds", SERVICE_ID, TEST, defaultTestCharge.getExternalChargeId()))
                        .then().statusCode(ACCEPTED.getStatusCode());

                // Verify refund summary on charge
                app.givenSetup()
                        .get(format("/v1/api/accounts/%s/charges/%s/", accountId, defaultTestCharge.getExternalChargeId()))
                        .then()
                        .statusCode(200)
                        .body("refund_summary.status", is("full"))
                        .body("refund_summary.amount_available", is(0))
                        .body("refund_summary.amount_submitted", is(100));
            }

            @ParameterizedTest
            @DisplayName("Should return 400 and not create refund entity if charge is not in refundable state")
            @MethodSource("nonRefundableChargeStatuses")
            void shouldFailRequestingARefund_whenChargeStatusMakesItNotRefundable(ChargeStatus nonRefundableChargeStatus) {
                DatabaseFixtures.TestCharge testCharge = DatabaseFixtures
                        .withDatabaseTestHelper(app.getDatabaseTestHelper())
                        .aTestCharge()
                        .withAmount(100L)
                        .withTestAccount(defaultTestAccount)
                        .withChargeStatus(nonRefundableChargeStatus)
                        .insert();

                int refundAmount = 20;

                // Attempt to submit refund
                app.givenSetup()
                        .body(toJson(Map.of(
                                "amount", refundAmount,
                                "refund_amount_available", defaultTestCharge.getAmount()

                        )))
                        .post(format("/v1/api/service/%s/account/%s/charges/%s/refunds", SERVICE_ID, TEST, testCharge.getExternalChargeId()))
                        .then().statusCode(BAD_REQUEST.getStatusCode())
                        .body("message", contains(format("Charge with id [%s] not available for refund.", testCharge.getExternalChargeId())))
                        .body("error_identifier", is(ErrorIdentifier.REFUND_NOT_AVAILABLE.toString()));

                // Verify no refund entity created
                List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
                assertThat(refundsFoundByChargeExternalId.size(), is(0));
            }

            private static final Stream<ChargeStatus> NON_REFUNDABLE_CHARGE_STATUSES = Arrays.stream(ChargeStatus.values()).filter(chargeStatus -> !chargeStatus.equals(CAPTURED));

            private static Stream<ChargeStatus> nonRefundableChargeStatuses() {
                return NON_REFUNDABLE_CHARGE_STATUSES;
            }

            @Test
            @DisplayName("Should return 400 and not create refund entity when charge is fully refunded")
            void shouldFailRequestingARefund_whenChargeRefundIsFull() {
                int refundAmount = (int) defaultTestCharge.getAmount();
                String externalChargeId = defaultTestCharge.getExternalChargeId();

                // Fully refund charge
                app.givenSetup()
                        .body(toJson(Map.of(
                                "amount", refundAmount,
                                "refund_amount_available", defaultTestCharge.getAmount()

                        )))
                        .post(format("/v1/api/service/%s/account/%s/charges/%s/refunds", SERVICE_ID, TEST, defaultTestCharge.getExternalChargeId()))
                        .then().statusCode(ACCEPTED.getStatusCode());

                // Verify refund exists in database
                List<Map<String, Object>> firstRefundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(externalChargeId);
                assertThat(firstRefundsFoundByChargeExternalId.size(), is(1));

                // Attempt to submit additional refund
                app.givenSetup()
                        .body(toJson(Map.of(
                                "amount", refundAmount,
                                "refund_amount_available", defaultTestCharge.getAmount()

                        )))
                        .post(format("/v1/api/service/%s/account/%s/charges/%s/refunds", SERVICE_ID, TEST, defaultTestCharge.getExternalChargeId()))
                        .then().statusCode(BAD_REQUEST.getStatusCode())
                        .body("reason", is("full"))
                        .body("message", contains(format("Charge with id [%s] not available for refund.", externalChargeId)))
                        .body("error_identifier", is(ErrorIdentifier.REFUND_NOT_AVAILABLE.toString()));

                // Verify refund no extra refund created in database
                List<Map<String, Object>> secondRefundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(externalChargeId);
                assertThat(secondRefundsFoundByChargeExternalId.size(), is(1));
            }

            @Test
            @DisplayName("Should return 400 and not create refund entity when requested refund is greater than charge amount")
            void shouldFailRequestingARefund_whenAmountIsBiggerThanChargeAmount() {
                long refundAmount = defaultTestCharge.getAmount() + 20;

                // Attempt to submit refund
                app.givenSetup()
                        .body(toJson(Map.of(
                                "amount", refundAmount,
                                "refund_amount_available", defaultTestCharge.getAmount()

                        )))
                        .post(format("/v1/api/service/%s/account/%s/charges/%s/refunds", SERVICE_ID, TEST, defaultTestCharge.getExternalChargeId()))
                        .then().statusCode(BAD_REQUEST.getStatusCode())
                        .body("reason", is("amount_not_available"))
                        .body("message", contains("Not sufficient amount available for refund"))
                        .body("error_identifier", is(ErrorIdentifier.REFUND_NOT_AVAILABLE.toString()));

                // Verify no refund or history created in database
                List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
                assertThat(refundsFoundByChargeExternalId.size(), is(0));
                List<String> refundsHistory = app.getDatabaseTestHelper().getRefundsHistoryByChargeExternalId(defaultTestCharge.getExternalChargeId()).stream().map(x -> x.get("status").toString()).collect(Collectors.toList());
                assertThat(refundsHistory.size(), is(0));
            }

            @Test
            @DisplayName("Should return 400 and not create refund entity when requested refund is greater than max charge amount")
            void shouldFailRequestingARefund_whenAmountIsBiggerThanAllowedChargeAmount() {
                Long refundAmount = 10000001L;

                // Attempt to submit refund
                app.givenSetup()
                        .body(toJson(Map.of(
                                "amount", refundAmount,
                                "refund_amount_available", defaultTestCharge.getAmount()

                        )))
                        .post(format("/v1/api/service/%s/account/%s/charges/%s/refunds", SERVICE_ID, TEST, defaultTestCharge.getExternalChargeId()))
                        .then().statusCode(BAD_REQUEST.getStatusCode())
                        .body("reason", is("amount_not_available"))
                        .body("message", contains("Not sufficient amount available for refund"))
                        .body("error_identifier", is(ErrorIdentifier.REFUND_NOT_AVAILABLE.toString()));

                // Verify no refund or history created in database
                List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
                assertThat(refundsFoundByChargeExternalId.size(), is(0));
                List<String> refundsHistory = app.getDatabaseTestHelper().getRefundsHistoryByChargeExternalId(defaultTestCharge.getExternalChargeId()).stream().map(x -> x.get("status").toString()).collect(Collectors.toList());
                assertThat(refundsHistory.size(), is(0));
            }

            @Test
            @DisplayName("Should return 400 and not create refund entity when requested refund is less than minimum charge amount")
            void shouldFailRequestingARefund_whenAmountIsLessThanOnePence() {
                long refundAmount = 0L;

                // Attempt to submit refund
                app.givenSetup()
                        .body(toJson(Map.of(
                                "amount", refundAmount,
                                "refund_amount_available", defaultTestCharge.getAmount()

                        )))
                        .post(format("/v1/api/service/%s/account/%s/charges/%s/refunds", SERVICE_ID, TEST, defaultTestCharge.getExternalChargeId()))
                        .then().statusCode(BAD_REQUEST.getStatusCode())
                        .body("reason", is("amount_min_validation"))
                        .body("message", contains("Validation error for amount. Minimum amount for a refund is 1"))
                        .body("error_identifier", is(ErrorIdentifier.REFUND_NOT_AVAILABLE.toString()));

                // Verify no refund or history created in database
                List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
                assertThat(refundsFoundByChargeExternalId.size(), is(0));
                List<String> refundsHistory = app.getDatabaseTestHelper().getRefundsHistoryByChargeExternalId(defaultTestCharge.getExternalChargeId()).stream().map(x -> x.get("status").toString()).collect(Collectors.toList());
                assertThat(refundsHistory.size(), is(0));
            }


            @Test
            @DisplayName("Should return 400 and not create refund entity when requested partial refund is greater than amount available")
            void shouldFailRequestingARefund_whenAPartialRefundMakesTotalRefundedAmountBiggerThanChargeAmount() {
                long firstRefundAmount = 80L;
                long secondRefundAmount = 30L; // 10 more than available

                // Partially refund charge 
                app.givenSetup()
                        .body(toJson(Map.of(
                                "amount", firstRefundAmount,
                                "refund_amount_available", defaultTestCharge.getAmount()

                        )))
                        .post(format("/v1/api/service/%s/account/%s/charges/%s/refunds", SERVICE_ID, TEST, defaultTestCharge.getExternalChargeId()))
                        .then().statusCode(ACCEPTED.getStatusCode());

                // Verify partial refund exists in DB
                List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
                assertThat(refundsFoundByChargeExternalId.size(), is(1));

                // Attempt to submit second refund, exceeding amount available
                app.givenSetup()
                        .body(toJson(Map.of(
                                "amount", secondRefundAmount,
                                "refund_amount_available", defaultTestCharge.getAmount() - firstRefundAmount

                        )))
                        .post(format("/v1/api/service/%s/account/%s/charges/%s/refunds", SERVICE_ID, TEST, defaultTestCharge.getExternalChargeId()))
                        .then().statusCode(BAD_REQUEST.getStatusCode())
                        .body("reason", is("amount_not_available"))
                        .body("message", contains("Not sufficient amount available for refund"))
                        .body("error_identifier", is(ErrorIdentifier.REFUND_NOT_AVAILABLE.toString()));


                // Verify no additional refund is created in DB
                List<Map<String, Object>> secondRefundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
                assertThat(secondRefundsFoundByChargeExternalId.size(), is(1));
            }

            @Nested
            @DisplayName("When charge is historic")
            class HistoricCharge {
                @Test
                @DisplayName("Should create refund entity and update refund history when refund is not full and existing partial refunds have been expunged")
                void shouldSucceedRequestingARefundForHistoricCharge_whenExistingPartialRefundsHaveBeenExpunged() throws Exception {
                    String chargeExternalId = "historic-charge-id-xxxxxxx"; // padded to 26 characters because this is stored as a char(26) which is automatically padded with whitespace

                    // set up ledger to return charge with refund status available
                    ChargeResponse.RefundSummary refundSummary = new ChargeResponse.RefundSummary();
                    refundSummary.setStatus("available");
                    LedgerTransaction charge = aValidLedgerTransaction()
                            .withExternalId(chargeExternalId)
                            .withGatewayAccountId(defaultTestAccount.getAccountId())
                            .withAmount(1000L)
                            .withRefundSummary(refundSummary)
                            .withPaymentProvider(PAYMENT_PROVIDER)
                            .build();
                    app.getLedgerStub().returnLedgerTransaction(chargeExternalId, defaultTestAccount.getAccountId(), charge);

                    // add one refund that is still in connector and another that is only in ledger to check
                    // that both are used when calculating refundability
                    app.getDatabaseTestHelper().addRefund("connector-refund-id-xxxxxx", 500L, RefundStatus.REFUNDED, "refund-gateway-id-1", ZonedDateTime.now(), chargeExternalId);

                    LedgerTransaction expungedRefund = aValidLedgerTransaction()
                            .withExternalId("ledger-refund-id")
                            .withGatewayAccountId(defaultTestAccount.getAccountId())
                            .withParentTransactionId(chargeExternalId)
                            .withAmount(300L)
                            .withStatus(ExternalRefundStatus.EXTERNAL_SUCCESS.getStatus())
                            .withPaymentProvider(PAYMENT_PROVIDER)
                            .build();
                    app.getLedgerStub().returnRefundsForPayment(chargeExternalId, List.of(expungedRefund));

                    // Attempt to submit refund
                    int refundAmount = 200;
                    String refundId = app.givenSetup()
                            .body(Map.of(
                                    "amount", refundAmount,
                                    "refund_amount_available", 200

                            ))
                            .post(format("/v1/api/service/%s/account/%s/charges/%s/refunds", SERVICE_ID, TEST, chargeExternalId))
                            .then()
                            .statusCode(ACCEPTED.getStatusCode())
                            .extract().path("refund_id");

//                  Verify refund entity created in database
                    List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(chargeExternalId);
                    assertThat(refundsFoundByChargeExternalId.size(), is(2));
                    assertThat(refundsFoundByChargeExternalId, hasItems(
                            aRefundMatching("connector-refund-id-xxxxxx", is(notNullValue()), chargeExternalId, 500L, "REFUNDED"),
                            aRefundMatching(refundId, is(notNullValue()), chargeExternalId, refundAmount, "REFUNDED")
                    ));

                    // Verify refund history
                    List<String> refundsHistory = app.getDatabaseTestHelper().getRefundsHistoryByRefundExternalId(refundId).stream().map(x -> x.get("status").toString()).collect(Collectors.toList());
                    assertThat(refundsHistory.size(), is(3));
                    assertThat(refundsHistory, contains("REFUNDED", "REFUND SUBMITTED", "CREATED"));
                }

                @Test
                @DisplayName("Should return 400 and not create refund entity when requesting a partial refund greater than amount available, and existing refunds have been expunged")
                void shouldFailRequestingARefundForHistoricCharge_whenPartialRefundAmountGreaterThanRemainingAmount_whenExistingPartialRefundsHaveBeenExpunged() throws Exception {
                    String chargeExternalId = "historic-charge-id";

                    // set up ledger to return charge with refund status available
                    ChargeResponse.RefundSummary refundSummary = new ChargeResponse.RefundSummary();
                    refundSummary.setStatus("available");
                    LedgerTransaction charge = aValidLedgerTransaction()
                            .withExternalId(chargeExternalId)
                            .withGatewayAccountId(defaultTestAccount.getAccountId())
                            .withAmount(1000L)
                            .withRefundSummary(refundSummary)
                            .withPaymentProvider(PAYMENT_PROVIDER)
                            .build();
                    app.getLedgerStub().returnLedgerTransaction(chargeExternalId, defaultTestAccount.getAccountId(), charge);

                    // add one refund that is still in connector and another that is only in ledger to check
                    // that both are used when calculating refundability
                    app.getDatabaseTestHelper().addRefund("connector-refund-id", 500L, RefundStatus.CREATED, "refund-gateway-id-1", ZonedDateTime.now(), chargeExternalId);

                    LedgerTransaction expungedRefund = aValidLedgerTransaction()
                            .withExternalId("ledger-refund-id")
                            .withGatewayAccountId(defaultTestAccount.getAccountId())
                            .withParentTransactionId(chargeExternalId)
                            .withAmount(300L)
                            .withStatus(ExternalRefundStatus.EXTERNAL_SUCCESS.getStatus())
                            .withPaymentProvider(PAYMENT_PROVIDER)
                            .build();
                    app.getLedgerStub().returnRefundsForPayment(chargeExternalId, List.of(expungedRefund));

                    Long refundAmount = 201L;

                    // Attempt to submit refund
                    app.givenSetup()
                            .body(toJson(Map.of(
                                    "amount", refundAmount,
                                    "refund_amount_available", 200L

                            )))
                            .post(format("/v1/api/service/%s/account/%s/charges/%s/refunds", SERVICE_ID, TEST, chargeExternalId))
                            .then().statusCode(BAD_REQUEST.getStatusCode())
                            .body("reason", is("amount_not_available"))
                            .body("message", contains("Not sufficient amount available for refund"))
                            .body("error_identifier", is(ErrorIdentifier.REFUND_NOT_AVAILABLE.toString()));
                }

                @Test
                @DisplayName("Should return 500 and not create refund entity when ledger returns an error finding refunds for charge")
                void shouldErrorRequestingARefundForHistoricCharge_whenErrorReturnedByLedger() throws Exception {
                    String chargeExternalId = "historic-charge-id";

                    ChargeResponse.RefundSummary refundSummary = new ChargeResponse.RefundSummary();
                    refundSummary.setStatus("available");
                    LedgerTransaction charge = aValidLedgerTransaction()
                            .withExternalId(chargeExternalId)
                            .withGatewayAccountId(defaultTestAccount.getAccountId())
                            .withAmount(1000L)
                            .withRefundSummary(refundSummary)
                            .build();
                    app.getLedgerStub().returnLedgerTransaction(chargeExternalId, defaultTestAccount.getAccountId(), charge);

                    app.getLedgerStub().returnErrorForFindRefundsForPayment(chargeExternalId);

                    // Attempt to submit refund
                    app.givenSetup()
                            .body(toJson(Map.of(
                                    "amount", 200L,
                                    "refund_amount_available", 200L

                            )))
                            .post(format("/v1/api/service/%s/account/%s/charges/%s/refunds", SERVICE_ID, TEST, chargeExternalId))
                            .then().statusCode(INTERNAL_SERVER_ERROR.getStatusCode());

                    // Verify no refunds created in database
                    List<Map<String, Object>> refunds = app.getDatabaseTestHelper().getRefundsByChargeExternalId(chargeExternalId);
                    assertThat(refunds, hasSize(0));
                }

            }
        }
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
                        .replace("{accountId}", String.valueOf(accountId))
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
