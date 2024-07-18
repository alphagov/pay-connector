package uk.gov.pay.connector.it.resources.worldpay;

import com.google.gson.Gson;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToXml;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.lang.String.format;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_CODE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.ONE_OFF_CUSTOMER_INITIATED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.RETIRED;
import static uk.gov.pay.connector.matcher.RefundsMatcher.aRefundMatching;
import static uk.gov.pay.connector.rules.WorldpayMockClient.WORLDPAY_URL;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_REFUND_WORLDPAY_REQUEST;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomLong;

public class WorldpayRefundSubmitResourceIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    private static String SERVICE_ID = "a-valid-service-id";
    
    private long defaultAccountId;
    private long defaultCredentialsId;
    private DatabaseFixtures.TestAccount defaultTestAccount;
    private DatabaseFixtures.TestCharge defaultTestCharge;
    
    private static final Map<String, Object> validWorldpayCredentials = Map.of(
            ONE_OFF_CUSTOMER_INITIATED, Map.of(
                    CREDENTIALS_MERCHANT_CODE, "merchant-id",
                    CREDENTIALS_USERNAME, "test-user",
                    CREDENTIALS_PASSWORD, "test-password")
    );

    @BeforeEach
    void setUpCharge() {
        defaultAccountId = randomLong();
        defaultCredentialsId = randomLong();
        
        var credentialParams = anAddGatewayAccountCredentialsParams()
                .withId(defaultCredentialsId)
                .withPaymentProvider("worldpay")
                .withGatewayAccountId(defaultAccountId)
                .withState(ACTIVE)
                .withCredentials(validWorldpayCredentials)
                .build();
        
        defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestAccount()
                .withAccountId(defaultAccountId)
                .withGatewayAccountCredentials(List.of(credentialParams))
                .withServiceId(SERVICE_ID)
                .withType(TEST)
                .insert();

        defaultTestCharge = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withAmount(100L)
                .withTransactionId("MyUniqueTransactionId!")
                .withTestAccount(defaultTestAccount)
                .withChargeStatus(CAPTURED)
                .withPaymentProvider("worldpay")
                .withGatewayCredentialId(defaultCredentialsId)
                .insert();
    }

    @Nested
    class ByAccountId {
        @Test
        void shouldBeAbleToRequestARefund_partialAmount() {
            Long refundAmount = 50L;

            app.getWorldpayMockClient().mockRefundSuccess();
            ValidatableResponse validatableResponse = postRefundFor(defaultAccountId, defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount());
            String refundId = assertRefundResponseWith(defaultAccountId, defaultTestCharge.getExternalChargeId(), refundAmount, validatableResponse, ACCEPTED.getStatusCode());

            List<Map<String, Object>> refundsFoundByChargeExternalId = (app.getDatabaseTestHelper()).getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
            assertThat(refundsFoundByChargeExternalId.size(), is(1));
            assertThat(refundsFoundByChargeExternalId, hasItems(aRefundMatching(refundId, is(notNullValue()), defaultTestCharge.getExternalChargeId(), refundAmount, "REFUND SUBMITTED")));

            List<String> refundsHistory = (app.getDatabaseTestHelper()).getRefundsHistoryByChargeExternalId(defaultTestCharge.getExternalChargeId()).stream().map(x -> x.get("status").toString()).collect(Collectors.toList());
            assertThat(refundsHistory.size(), is(2));
            assertThat(refundsHistory, containsInAnyOrder("REFUND SUBMITTED", "CREATED"));
        }

        @Test
        void shouldBeAbleToRequestARefund_fullAmount() {

            Long refundAmount = defaultTestCharge.getAmount();

            app.getWorldpayMockClient().mockRefundSuccess();
            ValidatableResponse validatableResponse = postRefundFor(defaultAccountId, defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount());
            String refundId = assertRefundResponseWith(defaultAccountId, defaultTestCharge.getExternalChargeId(), refundAmount, validatableResponse, ACCEPTED.getStatusCode());

            List<Map<String, Object>> refundsFoundByChargeExternalId = (app.getDatabaseTestHelper()).getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
            assertThat(refundsFoundByChargeExternalId.size(), is(1));
            assertThat(refundsFoundByChargeExternalId, hasItems(aRefundMatching(refundId, is(notNullValue()), defaultTestCharge.getExternalChargeId(), refundAmount, "REFUND SUBMITTED")));
            assertThat(refundsFoundByChargeExternalId.get(0), hasEntry("charge_external_id", defaultTestCharge.getExternalChargeId()));
            assertThat(refundsFoundByChargeExternalId.get(0), hasEntry("gateway_transaction_id", refundId));

            String expectedRequestBody = TestTemplateResourceLoader.load(WORLDPAY_VALID_REFUND_WORLDPAY_REQUEST)
                    .replace("{{merchantCode}}", "merchant-id")
                    .replace("{{transactionId}}", "MyUniqueTransactionId!")
                    .replace("{{refundReference}}", refundsFoundByChargeExternalId.get(0).get("external_id").toString())
                    .replace("{{amount}}", "100");

            verifyRequestBodyToWorldpay(WORLDPAY_URL, expectedRequestBody);
        }

        @Test
        void shouldBeAbleToRequestForRetiredCredentials() {
            app.getWorldpayMockClient().mockRefundSuccess();
            long accountId = randomLong();
            
            AddGatewayAccountCredentialsParams stripeCredentialsParams = anAddGatewayAccountCredentialsParams()
                    .withPaymentProvider(STRIPE.getName())
                    .withGatewayAccountId(accountId)
                    .withState(ACTIVE)
                    .build();
            
            AddGatewayAccountCredentialsParams worldpayCredentialsParams = anAddGatewayAccountCredentialsParams()
                    .withPaymentProvider(WORLDPAY.getName())
                    .withGatewayAccountId(accountId)
                    .withState(RETIRED)
                    .withCredentials(validWorldpayCredentials)
                    .build();

            var testAccountWithRetiredCredentials = DatabaseFixtures
                    .withDatabaseTestHelper(app.getDatabaseTestHelper())
                    .aTestAccount()
                    .withAccountId(accountId)
                    .withGatewayAccountCredentials(List.of(stripeCredentialsParams, worldpayCredentialsParams))
                    .insert();
            
            DatabaseFixtures.TestCharge charge = DatabaseFixtures
                    .withDatabaseTestHelper(app.getDatabaseTestHelper())
                    .aTestCharge()
                    .withAmount(100L)
                    .withTransactionId("MyUniqueTransactionId2!")
                    .withTestAccount(testAccountWithRetiredCredentials)
                    .withChargeStatus(CAPTURED)
                    .withPaymentProvider(WORLDPAY.getName())
                    .insert();

            Long refundAmount = 100L;
            
            ValidatableResponse validatableResponse = postRefundFor(accountId, charge.getExternalChargeId(), refundAmount, charge.getAmount());
            assertRefundResponseWith(accountId, charge.getExternalChargeId(), refundAmount, validatableResponse, ACCEPTED.getStatusCode());

            app.getWorldpayWireMockServer().verify(postRequestedFor(urlPathEqualTo(WORLDPAY_URL)));
        }

        @Test
        void shouldBeAbleToRequestARefund_multiplePartialAmounts_andRefundShouldBeInFullStatus() {

            Long firstRefundAmount = 80L;
            Long secondRefundAmount = 20L;
            String externalChargeId = defaultTestCharge.getExternalChargeId();

            app.getWorldpayMockClient().mockRefundSuccess();
            ValidatableResponse firstValidatableResponse = postRefundFor(defaultAccountId, externalChargeId, firstRefundAmount, defaultTestCharge.getAmount());
            String firstRefundId = assertRefundResponseWith(defaultAccountId, externalChargeId, firstRefundAmount, firstValidatableResponse, ACCEPTED.getStatusCode());

            app.getWorldpayMockClient().mockRefundSuccess();
            ValidatableResponse secondValidatableResponse = postRefundFor(defaultAccountId, externalChargeId, secondRefundAmount, defaultTestCharge.getAmount() - firstRefundAmount);
            String secondRefundId = assertRefundResponseWith(defaultAccountId, externalChargeId, secondRefundAmount, secondValidatableResponse, ACCEPTED.getStatusCode());

            List<Map<String, Object>> refundsFoundByChargeExternalId = (app.getDatabaseTestHelper()).getRefundsByChargeExternalId(externalChargeId);
            assertThat(refundsFoundByChargeExternalId.size(), is(2));

            assertThat(refundsFoundByChargeExternalId, hasItems(
                    aRefundMatching(secondRefundId, is(notNullValue()), externalChargeId, secondRefundAmount, "REFUND SUBMITTED"),
                    aRefundMatching(firstRefundId, is(notNullValue()), externalChargeId, firstRefundAmount, "REFUND SUBMITTED")));

            app.givenSetup()
                    .get(format("/v1/api/accounts/%s/charges/%s", defaultAccountId, externalChargeId))
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
                    .withChargeStatus(CAPTURE_SUBMITTED)
                    .withPaymentProvider("worldpay")
                    .withGatewayCredentialId(defaultCredentialsId)
                    .insert();

            Long refundAmount = 20L;

            app.getWorldpayMockClient().mockRefundSuccess();
            postRefundFor(defaultAccountId, testCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount())
                    .statusCode(BAD_REQUEST.getStatusCode())
                    .body("reason", is("pending"))
                    .body("message", contains(format("Charge with id [%s] not available for refund.", testCharge.getExternalChargeId())))
                    .body("error_identifier", is(ErrorIdentifier.REFUND_NOT_AVAILABLE.toString()));

            List<Map<String, Object>> refundsFoundByChargeExternalId = (app.getDatabaseTestHelper()).getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
            assertThat(refundsFoundByChargeExternalId.size(), is(0));
        }

        @Test
        void shouldFailRequestingARefund_whenChargeRefundIsFull() {

            Long refundAmount = defaultTestCharge.getAmount();
            String externalChargeId = defaultTestCharge.getExternalChargeId();
            Long chargeId = defaultTestCharge.getChargeId();

            app.getWorldpayMockClient().mockRefundSuccess();
            postRefundFor(defaultAccountId, externalChargeId, refundAmount, defaultTestCharge.getAmount())
                    .statusCode(ACCEPTED.getStatusCode());

            postRefundFor(defaultAccountId, externalChargeId, 1L, 0L)
                    .statusCode(BAD_REQUEST.getStatusCode())
                    .body("reason", is("full"))
                    .body("message", contains(format("Charge with id [%s] not available for refund.", externalChargeId)))
                    .body("error_identifier", is(ErrorIdentifier.REFUND_NOT_AVAILABLE.toString()));

            List<Map<String, Object>> refundsFoundByChargeExternalId = (app.getDatabaseTestHelper()).getRefundsByChargeExternalId(externalChargeId);
            assertThat(refundsFoundByChargeExternalId.size(), is(1));
        }

        @Test
        void shouldFailRequestingARefund_whenAmountIsBiggerThanChargeAmount() {
            Long refundAmount = defaultTestCharge.getAmount() + 20;
            app.getWorldpayMockClient().mockRefundSuccess();
            postRefundFor(defaultAccountId, defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount())
                    .statusCode(BAD_REQUEST.getStatusCode())
                    .body("reason", is("amount_not_available"))
                    .body("message", contains("Not sufficient amount available for refund"))
                    .body("error_identifier", is(ErrorIdentifier.REFUND_NOT_AVAILABLE.toString()));

            List<Map<String, Object>> refundsFoundByChargeExternalId = (app.getDatabaseTestHelper()).getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
            assertThat(refundsFoundByChargeExternalId.size(), is(0));
        }

        @Test
        void shouldFailRequestingARefund_whenAmountIsBiggerThanAllowedChargeAmount() {

            Long refundAmount = 10000001L;

            postRefundFor(defaultAccountId, defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount())
                    .statusCode(BAD_REQUEST.getStatusCode())
                    .body("reason", is("amount_not_available"))
                    .body("message", contains("Not sufficient amount available for refund"))
                    .body("error_identifier", is(ErrorIdentifier.REFUND_NOT_AVAILABLE.toString()));

            List<Map<String, Object>> refundsFoundByChargeExternalId = (app.getDatabaseTestHelper()).getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
            assertThat(refundsFoundByChargeExternalId.size(), is(0));
        }

        @Test
        void shouldFailRequestingARefund_whenAmountIsLessThanOnePence() {

            Long refundAmount = 0L;

            postRefundFor(defaultAccountId, defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount())
                    .statusCode(BAD_REQUEST.getStatusCode())
                    .body("reason", is("amount_min_validation"))
                    .body("message", contains("Validation error for amount. Minimum amount for a refund is 1"))
                    .body("error_identifier", is(ErrorIdentifier.REFUND_NOT_AVAILABLE.toString()));

            List<Map<String, Object>> refundsFoundByChargeExternalId = (app.getDatabaseTestHelper()).getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
            assertThat(refundsFoundByChargeExternalId.size(), is(0));
        }

        @Test
        void shouldFailRequestingARefund_whenAPartialRefundMakesTotalRefundedAmountBiggerThanChargeAmount() {
            Long firstRefundAmount = 80L;
            Long secondRefundAmount = 30L; // 10 more than available

            app.getWorldpayMockClient().mockRefundSuccess();
            ValidatableResponse validatableResponse = postRefundFor(defaultAccountId, defaultTestCharge.getExternalChargeId(), firstRefundAmount, defaultTestCharge.getAmount());
            String firstRefundId = assertRefundResponseWith(defaultAccountId, defaultTestCharge.getExternalChargeId(), firstRefundAmount, validatableResponse, ACCEPTED.getStatusCode());

            List<Map<String, Object>> refundsFoundByChargeExternalId = (app.getDatabaseTestHelper()).getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
            assertThat(refundsFoundByChargeExternalId.size(), is(1));
            assertThat(refundsFoundByChargeExternalId, hasItems(aRefundMatching(firstRefundId, is(notNullValue()), defaultTestCharge.getExternalChargeId(), firstRefundAmount, "REFUND SUBMITTED")));

            app.getWorldpayMockClient().mockRefundSuccess();
            postRefundFor(defaultAccountId, defaultTestCharge.getExternalChargeId(), secondRefundAmount, defaultTestCharge.getAmount() - firstRefundAmount)
                    .statusCode(400)
                    .body("reason", is("amount_not_available"))
                    .body("message", contains("Not sufficient amount available for refund"))
                    .body("error_identifier", is(ErrorIdentifier.REFUND_NOT_AVAILABLE.toString()));

            List<Map<String, Object>> refundsFoundByChargeExternalId1 = (app.getDatabaseTestHelper()).getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
            assertThat(refundsFoundByChargeExternalId1.size(), is(1));
            assertThat(refundsFoundByChargeExternalId1, hasItems(aRefundMatching(firstRefundId, is(notNullValue()), defaultTestCharge.getExternalChargeId(), firstRefundAmount, "REFUND SUBMITTED")));
        }

        @Test
        void shouldFailRequestingARefund_whenGatewayOperationFails() {
            Long refundAmount = defaultTestCharge.getAmount();

            app.getWorldpayMockClient().mockRefundError();
            postRefundFor(defaultAccountId, defaultTestCharge.getExternalChargeId(), refundAmount, defaultTestCharge.getAmount())
                    .statusCode(INTERNAL_SERVER_ERROR.getStatusCode())
                    .body("message", contains("Worldpay refund response (error code: 2, error: Something went wrong.)"))
                    .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));

            java.util.List<Map<String, Object>> refundsFoundByChargeExternalId = (app.getDatabaseTestHelper()).getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
            assertThat(refundsFoundByChargeExternalId.size(), is(1));
            assertThat(refundsFoundByChargeExternalId, contains(
                    allOf(
                            hasEntry("amount", (Object) refundAmount),
                            hasEntry("status", RefundStatus.REFUND_ERROR.getValue()),
                            hasEntry("charge_external_id", defaultTestCharge.getExternalChargeId())
                    )));
        }
    }
    
    @Nested
    class ByServiceIdAndType {
        @Test
        void shouldBeAbleToRequestARefund_partialAmount() {
            Long refundAmount = 50L;
            app.getWorldpayMockClient().mockRefundSuccess();

            var response = app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", refundAmount,
                            "refund_amount_available", defaultTestCharge.getAmount()
                    )))
                    .post(format("/v1/api/service/%s/account/%s/charges/%s/refunds", SERVICE_ID, TEST, defaultTestCharge.getExternalChargeId()))
                    .then()
                    .statusCode(202)
                    .body("refund_id", is(notNullValue()))
                    .body("amount", is(refundAmount.intValue()))
                    .body("status", is("submitted"))
                    .body("created_date", is(notNullValue()));
            
            String refundId = response.extract().path("refund_id");

            String paymentUrl = format("https://localhost:%s/v1/api/service/%s/account/%s/charges/%s",
                    app.getLocalPort(), SERVICE_ID, TEST, defaultTestCharge.getExternalChargeId());

            response.body("_links.self.href", is(paymentUrl + "/refunds/" + refundId))
                    .body("_links.payment.href", is(paymentUrl));
            
            List<Map<String, Object>> refundsFoundByChargeExternalId = (app.getDatabaseTestHelper()).getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
            assertThat(refundsFoundByChargeExternalId.size(), is(1));
            assertThat(refundsFoundByChargeExternalId, hasItems(aRefundMatching(refundId, is(notNullValue()), defaultTestCharge.getExternalChargeId(), refundAmount, "REFUND SUBMITTED")));

            List<String> refundsHistory = (app.getDatabaseTestHelper()).getRefundsHistoryByChargeExternalId(defaultTestCharge.getExternalChargeId()).stream().map(x -> x.get("status").toString()).collect(Collectors.toList());
            assertThat(refundsHistory.size(), is(2));
            assertThat(refundsHistory, containsInAnyOrder("REFUND SUBMITTED", "CREATED"));

            String expectedRequestBody = TestTemplateResourceLoader.load(WORLDPAY_VALID_REFUND_WORLDPAY_REQUEST)
                    .replace("{{merchantCode}}", "merchant-id")
                    .replace("{{transactionId}}", "MyUniqueTransactionId!")
                    .replace("{{refundReference}}", refundsFoundByChargeExternalId.get(0).get("external_id").toString())
                    .replace("{{amount}}", "50");

            verifyRequestBodyToWorldpay(WORLDPAY_URL, expectedRequestBody);
        }

        @Test
        void shouldBeAbleToRequestARefund_fullAmount() {
            Long refundAmount = defaultTestCharge.getAmount();
            app.getWorldpayMockClient().mockRefundSuccess();

            String refundId = app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", refundAmount,
                            "refund_amount_available", defaultTestCharge.getAmount()
                    )))
                    .post(format("/v1/api/service/%s/account/%s/charges/%s/refunds", SERVICE_ID, TEST, defaultTestCharge.getExternalChargeId()))
                    .then()
                    .statusCode(202)
                    .body("refund_id", is(notNullValue()))
                    .body("amount", is(refundAmount.intValue()))
                    .body("status", is("submitted"))
                    .body("created_date", is(notNullValue()))
                    .extract().path("refund_id");
            
            List<Map<String, Object>> refundsFoundByChargeExternalId = (app.getDatabaseTestHelper()).getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
            assertThat(refundsFoundByChargeExternalId.size(), is(1));
            assertThat(refundsFoundByChargeExternalId, hasItems(aRefundMatching(refundId, is(notNullValue()), defaultTestCharge.getExternalChargeId(), refundAmount, "REFUND SUBMITTED")));
            assertThat(refundsFoundByChargeExternalId.get(0), hasEntry("charge_external_id", defaultTestCharge.getExternalChargeId()));
            assertThat(refundsFoundByChargeExternalId.get(0), hasEntry("gateway_transaction_id", refundId));

            String expectedRequestBody = TestTemplateResourceLoader.load(WORLDPAY_VALID_REFUND_WORLDPAY_REQUEST)
                    .replace("{{merchantCode}}", "merchant-id")
                    .replace("{{transactionId}}", "MyUniqueTransactionId!")
                    .replace("{{refundReference}}", refundsFoundByChargeExternalId.get(0).get("external_id").toString())
                    .replace("{{amount}}", "100");

            verifyRequestBodyToWorldpay(WORLDPAY_URL, expectedRequestBody);
        }
    }

    private ValidatableResponse postRefundFor(long accountId, String chargeId, Long refundAmount, Long refundAmountAvlbl) {
        Map<String, Long> refundData = Map.of("amount", refundAmount, "refund_amount_available", refundAmountAvlbl);
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

    private String assertRefundResponseWith(long accountId, String chargeExternalId, Long refundAmount, ValidatableResponse validatableResponse, int expectedStatusCode) {
        ValidatableResponse response = validatableResponse
                .statusCode(expectedStatusCode)
                .body("refund_id", is(notNullValue()))
                .body("amount", is(refundAmount.intValue()))
                .body("status", is("submitted"))
                .body("created_date", is(notNullValue()));

        String paymentUrl = format("https://localhost:%s/v1/api/accounts/%s/charges/%s",
                app.getLocalPort(), accountId, chargeExternalId);

        String refundId = response.extract().path("refund_id");
        response.body("_links.self.href", is(paymentUrl + "/refunds/" + refundId))
                .body("_links.payment.href", is(paymentUrl));

        return refundId;
    }

    private void verifyRequestBodyToWorldpay(String path, String body) {
        app.getWorldpayWireMockServer().verify(
                postRequestedFor(urlPathEqualTo(path))
                        .withHeader("Content-Type", equalTo("application/xml"))
                        .withHeader("Authorization", equalTo("Basic dGVzdC11c2VyOnRlc3QtcGFzc3dvcmQ="))
                        .withRequestBody(equalToXml(body)));
    }
}
