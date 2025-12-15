package uk.gov.pay.connector.it.resources.stripe;

import com.google.common.collect.ImmutableMap;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.lang.String.format;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.eclipse.jetty.http.HttpStatus.ACCEPTED_202;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class StripeRefundsResourceIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();

    private final String stripeAccountId = "stripe_account_id";
    private final String accountId = String.valueOf(RandomIdGenerator.randomLong());
    private final String SERVICE_ID = "a-valid-service-id";
    private final String PLATFORM_ACCOUNT_ID = "stripe_platform_account_id"; // set in test-it-config.yaml
    
    private DatabaseFixtures.TestAccount defaultTestAccount;
    private DatabaseFixtures.TestCharge defaultTestCharge;
    
    @BeforeEach
    void setUpStripe() {
        defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestAccount()
                .withPaymentProvider("stripe")
                .withCredentials(ImmutableMap.of("stripe_account_id", stripeAccountId))
                .withAccountId(Long.parseLong(accountId))
                .withServiceId(SERVICE_ID)
                .insert();

        defaultTestCharge = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withAmount(100L)
                .withTransactionId("pi_123")
                .withTestAccount(defaultTestAccount)
                .withChargeStatus(CAPTURED)
                .withPaymentProvider(STRIPE.getName())
                .insert();

        app.getStripeMockClient().mockGetPaymentIntent(defaultTestCharge.getTransactionId());
    }
    
    @Nested
    class ByAccountId {
        @Nested
        class SubmitRefund {
            @Test
            void shouldSuccessfullyRefund_usingChargeId() {
                var stripeCharge = DatabaseFixtures
                        .withDatabaseTestHelper(app.getDatabaseTestHelper())
                        .aTestCharge()
                        .withAmount(100L)
                        .withTransactionId("ch_123")
                        .withTestAccount(defaultTestAccount)
                        .withChargeStatus(CAPTURED)
                        .withPaymentProvider(STRIPE.getName())
                        .insert();
                String externalChargeId = stripeCharge.getExternalChargeId();
                long refundAmount = 10L;
                app.getStripeMockClient().mockTransferSuccess();
                app.getStripeMockClient().mockRefundSuccess();

                ValidatableResponse response = app.givenSetup()
                        .body(toJson(Map.of(
                                "amount", refundAmount,
                                "refund_amount_available", stripeCharge.getAmount()
                        )))
                        .post("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds"
                                .replace("{accountId}", accountId)
                                .replace("{chargeId}", externalChargeId))
                        .then()
                        .statusCode(ACCEPTED_202);

                List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(stripeCharge.getExternalChargeId());
                assertThat(refundsFoundByChargeExternalId.size(), is(1));
                assertThat(refundsFoundByChargeExternalId.getFirst().get("status"), is("REFUNDED"));
                assertThat(refundsFoundByChargeExternalId.getFirst(), hasEntry("charge_external_id", stripeCharge.getExternalChargeId()));
                assertThat(refundsFoundByChargeExternalId.getFirst(), hasEntry("gateway_transaction_id", "re_1DRiccHj08j21DRiccHj08j2_test"));
                String refundId = response.extract().path("refund_id");

                app.getStripeWireMockServer().verify(postRequestedFor(urlEqualTo("/v1/refunds"))
                        .withHeader("Idempotency-Key", equalTo("refund" + refundId))
                        .withRequestBody(containing("charge=ch_123"))
                        .withRequestBody(containing("amount=" + refundAmount)));
                app.getStripeWireMockServer().verify(postRequestedFor(urlEqualTo("/v1/transfers"))
                        .withHeader("Idempotency-Key", equalTo("transfer_in" + refundId))
                        .withHeader("Stripe-Account", equalTo(stripeAccountId))
                        .withRequestBody(containing("transfer_group=" + stripeCharge.getExternalChargeId()))
                        .withRequestBody(containing("destination=" + PLATFORM_ACCOUNT_ID)));
            }

            @Test
            void shouldSuccessfullyRefund_usingPaymentIntentId() {
                String externalChargeId = defaultTestCharge.getExternalChargeId();
                long refundAmount = 10L;
                app.getStripeMockClient().mockGetPaymentIntent(defaultTestCharge.getTransactionId());
                app.getStripeMockClient().mockTransferSuccess();
                app.getStripeMockClient().mockRefundSuccess();

                ValidatableResponse response = app.givenSetup()
                        .body(toJson(Map.of(
                                "amount", refundAmount,
                                "refund_amount_available", defaultTestCharge.getAmount()
                        )))
                        .post("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds"
                                .replace("{accountId}", accountId)
                                .replace("{chargeId}", externalChargeId))
                        .then()
                        .statusCode(ACCEPTED_202);

                List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
                assertThat(refundsFoundByChargeExternalId.size(), is(1));
                assertThat(refundsFoundByChargeExternalId.getFirst().get("status"), is("REFUNDED"));
                String refundId = response.extract().path("refund_id");
                String paymentIntentUrl = "/v1/payment_intents/" + defaultTestCharge.getTransactionId() + "?expand%5B%5D=charges.data.balance_transaction";
                app.getStripeWireMockServer().verify(getRequestedFor(urlEqualTo(paymentIntentUrl)));
                app.getStripeWireMockServer().verify(postRequestedFor(urlEqualTo("/v1/refunds"))
                        .withHeader("Idempotency-Key", equalTo("refund" + refundId))
                        .withRequestBody(containing("charge=ch_123456"))
                        .withRequestBody(containing("amount=" + refundAmount)));
                app.getStripeWireMockServer().verify(postRequestedFor(urlEqualTo("/v1/transfers"))
                        .withHeader("Idempotency-Key", equalTo("transfer_in" + refundId))
                        .withHeader("Stripe-Account", equalTo(stripeAccountId))
                        .withRequestBody(containing("transfer_group=" + defaultTestCharge.getExternalChargeId()))
                        .withRequestBody(containing("destination=" + PLATFORM_ACCOUNT_ID)));
            }

            @Test
            void stripeRefund_shouldResultInRefundErrorIfRefundFails() {
                String externalChargeId = defaultTestCharge.getExternalChargeId();
                long refundAmount = 10L;
                app.getStripeMockClient().mockRefundError();

                app.givenSetup()
                        .body(toJson(Map.of(
                                "amount", refundAmount,
                                "refund_amount_available", defaultTestCharge.getAmount()
                        )))
                        .post("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds"
                                .replace("{accountId}", accountId)
                                .replace("{chargeId}", externalChargeId))
                        .then()
                        .statusCode(INTERNAL_SERVER_ERROR.getStatusCode());

                List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
                assertThat(refundsFoundByChargeExternalId.size(), is(1));
                assertThat(refundsFoundByChargeExternalId.getFirst().get("status"), is("REFUND ERROR"));
            }

            @Test
            void stripeRefund_shouldResultInRefundErrorIfTransferFails() {
                String externalChargeId = defaultTestCharge.getExternalChargeId();
                long refundAmount = 10L;

                app.getStripeMockClient().mockRefundSuccess();
                app.getStripeMockClient().mockTransferFailure();

                app.givenSetup()
                        .body(toJson(Map.of(
                                "amount", refundAmount,
                                "refund_amount_available", defaultTestCharge.getAmount()
                        )))
                        .post("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds"
                                .replace("{accountId}", accountId)
                                .replace("{chargeId}", externalChargeId))
                        .then()
                        .statusCode(INTERNAL_SERVER_ERROR.getStatusCode())
                        .body("message", contains("Stripe refund response (error code: expired_card, error: Your card has expired.)"))
                        .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));


                List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
                assertThat(refundsFoundByChargeExternalId.size(), is(1));
                assertThat(refundsFoundByChargeExternalId.getFirst().get("status"), is("REFUND ERROR"));
            }
        }
    }
    
    @Nested
    class ByServiceIdAndType {
        @Nested
        class SubmitRefund {
            @Test
            @DisplayName("Should create refund, send refund & transfer requests to Stripe, and update refund status to REFUNDED for successful charge refund")
            void shouldSuccessfullyRefund_usingChargeId() {
                app.getStripeMockClient().mockTransferSuccess();
                app.getStripeMockClient().mockRefundSuccess();
                
                var stripeCharge = DatabaseFixtures
                        .withDatabaseTestHelper(app.getDatabaseTestHelper())
                        .aTestCharge()
                        .withAmount(100L)
                        .withTransactionId("ch_123")
                        .withTestAccount(defaultTestAccount)
                        .withChargeStatus(CAPTURED)
                        .withPaymentProvider(STRIPE.getName())
                        .insert();
                long refundAmount = 10L;
                
                // Attempt to submit refund
                String refundId = app.givenSetup()
                        .body(toJson(Map.of(
                                "amount", refundAmount, "refund_amount_available",
                                stripeCharge.getAmount()
                        )))
                        .post(format("/v1/api/service/%s/account/%s/charges/%s/refunds", SERVICE_ID, TEST, stripeCharge.getExternalChargeId()))
                        .then()
                        .statusCode(ACCEPTED_202)
                        .extract().path("refund_id");

                // Verify refund entity created in database
                List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(stripeCharge.getExternalChargeId());
                assertThat(refundsFoundByChargeExternalId.size(), is(1));
                assertThat(refundsFoundByChargeExternalId.getFirst().get("status"), is("REFUNDED"));
                assertThat(refundsFoundByChargeExternalId.getFirst(), hasEntry("charge_external_id", stripeCharge.getExternalChargeId()));
                assertThat(refundsFoundByChargeExternalId.getFirst(), hasEntry("gateway_transaction_id", "re_1DRiccHj08j21DRiccHj08j2_test"));
                
                // Verify updates to refund status appear in refund history
                List<String> refundsStatusHistory = (app.getDatabaseTestHelper()).getRefundsHistoryByChargeExternalId(stripeCharge.getExternalChargeId()).stream().map(x -> x.get("status").toString()).collect(Collectors.toList());
                assertThat(refundsStatusHistory.size(), is(3));
                assertThat(refundsStatusHistory, containsInAnyOrder("REFUNDED", "REFUND SUBMITTED", "CREATED"));
                
                // Verify requests to Stripe
                app.getStripeWireMockServer().verify(postRequestedFor(urlEqualTo("/v1/refunds"))
                        .withHeader("Idempotency-Key", equalTo("refund" + refundId))
                        .withRequestBody(containing("charge=ch_123"))
                        .withRequestBody(containing("amount=" + refundAmount)));
                app.getStripeWireMockServer().verify(postRequestedFor(urlEqualTo("/v1/transfers"))
                        .withHeader("Idempotency-Key", equalTo("transfer_in" + refundId))
                        .withHeader("Stripe-Account", equalTo(stripeAccountId))
                        .withRequestBody(containing("transfer_group=" + stripeCharge.getExternalChargeId()))
                        .withRequestBody(containing("destination=" + PLATFORM_ACCOUNT_ID)));
            }

            @Test
            @DisplayName("Should create refund, send refund & transfer requests to Stripe, and update refund status to REFUNDED for successful payment intent refund")
            void shouldSuccessfullyRefund_usingPaymentIntentId() {
                String externalChargeId = defaultTestCharge.getExternalChargeId();
                long refundAmount = 10L;
                app.getStripeMockClient().mockGetPaymentIntent(defaultTestCharge.getTransactionId());
                app.getStripeMockClient().mockTransferSuccess();
                app.getStripeMockClient().mockRefundSuccess();

                // Attempt to submit refund
                String refundId = app.givenSetup()
                        .body(toJson(Map.of(
                                "amount", refundAmount,
                                "refund_amount_available", defaultTestCharge.getAmount()
                        )))
                        .post(format("/v1/api/service/%s/account/%s/charges/%s/refunds", SERVICE_ID, TEST, defaultTestCharge.getExternalChargeId()))
                        .then()
                        .statusCode(ACCEPTED_202)
                        .extract().path("refund_id");

                // Verify updates to refund status appear in refund history
                List<String> refundsStatusHistory = (app.getDatabaseTestHelper()).getRefundsHistoryByChargeExternalId(defaultTestCharge.getExternalChargeId()).stream().map(x -> x.get("status").toString()).collect(Collectors.toList());
                assertThat(refundsStatusHistory.size(), is(3));
                assertThat(refundsStatusHistory, containsInAnyOrder("REFUNDED", "REFUND SUBMITTED", "CREATED"));

                // Verify refund entity created in database
                List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
                assertThat(refundsFoundByChargeExternalId.size(), is(1));
                assertThat(refundsFoundByChargeExternalId.getFirst().get("status"), is("REFUNDED"));
                
                // Verify requests to Stripe
                String paymentIntentUrl = "/v1/payment_intents/" + defaultTestCharge.getTransactionId() + "?expand%5B%5D=charges.data.balance_transaction";
                app.getStripeWireMockServer().verify(getRequestedFor(urlEqualTo(paymentIntentUrl)));
                app.getStripeWireMockServer().verify(postRequestedFor(urlEqualTo("/v1/refunds"))
                        .withHeader("Idempotency-Key", equalTo("refund" + refundId))
                        .withRequestBody(containing("charge=ch_123456"))
                        .withRequestBody(containing("amount=" + refundAmount)));
                app.getStripeWireMockServer().verify(postRequestedFor(urlEqualTo("/v1/transfers"))
                        .withHeader("Idempotency-Key", equalTo("transfer_in" + refundId))
                        .withHeader("Stripe-Account", equalTo(stripeAccountId))
                        .withRequestBody(containing("transfer_group=" + defaultTestCharge.getExternalChargeId()))
                        .withRequestBody(containing("destination=" + PLATFORM_ACCOUNT_ID)));
            }
            
            @Test
            @DisplayName("Should return 500 and create refund entity with status 'REFUND ERROR' if refund request to Stripe fails")
            void stripeRefund_shouldResultInRefundErrorIfRefundFails() {
                long refundAmount = 10L;
                app.getStripeMockClient().mockRefundError();

                // Attempt to submit refund
                app.givenSetup()
                        .body(toJson(Map.of(
                                "amount", refundAmount, "refund_amount_available",
                                defaultTestCharge.getAmount()
                        )))
                        .post(format("/v1/api/service/%s/account/%s/charges/%s/refunds", SERVICE_ID, TEST, defaultTestCharge.getExternalChargeId()))
                        .then()
                        .statusCode(INTERNAL_SERVER_ERROR.getStatusCode());

                // Verify refund exists in database
                List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
                assertThat(refundsFoundByChargeExternalId.size(), is(1));
                assertThat(refundsFoundByChargeExternalId.getFirst().get("status"), is("REFUND ERROR"));


                // Verify updates to refund status appear in refund history
                List<String> refundsStatusHistory = (app.getDatabaseTestHelper()).getRefundsHistoryByChargeExternalId(defaultTestCharge.getExternalChargeId()).stream().map(x -> x.get("status").toString()).collect(Collectors.toList());
                assertThat(refundsStatusHistory.size(), is(2));
                assertThat(refundsStatusHistory, containsInAnyOrder("REFUND ERROR", "CREATED"));
            }


            @Test
            @DisplayName("Should return 500 and create refund entity with status 'REFUND ERROR' if transfer request to Stripe fails")
            void stripeRefund_shouldResultInRefundErrorIfTransferFails() {
                long refundAmount = 10L;

                app.getStripeMockClient().mockRefundSuccess();
                app.getStripeMockClient().mockTransferFailure();
                
                // Attempt to submit refund
                app.givenSetup()
                        .body(toJson(Map.of(
                                "amount", refundAmount, "refund_amount_available",
                                defaultTestCharge.getAmount()
                        )))
                        .post(format("/v1/api/service/%s/account/%s/charges/%s/refunds", SERVICE_ID, TEST, defaultTestCharge.getExternalChargeId()))
                        .then()
                        .statusCode(INTERNAL_SERVER_ERROR.getStatusCode())
                        .body("message", contains("Stripe refund response (error code: expired_card, error: Your card has expired.)"))
                        .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));

                // Verify refund exists in database
                List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
                assertThat(refundsFoundByChargeExternalId.size(), is(1));
                assertThat(refundsFoundByChargeExternalId.getFirst().get("status"), is("REFUND ERROR"));
                
                // Verify updates to refund status appear in refund history
                List<String> refundsStatusHistory = (app.getDatabaseTestHelper()).getRefundsHistoryByChargeExternalId(defaultTestCharge.getExternalChargeId()).stream().map(x -> x.get("status").toString()).collect(Collectors.toList());
                assertThat(refundsStatusHistory.size(), is(2));
                assertThat(refundsStatusHistory, containsInAnyOrder("REFUND ERROR", "CREATED"));
            }
        }
    }
}
