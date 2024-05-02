package uk.gov.pay.connector.it.resources.stripe;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.eclipse.jetty.http.HttpStatus.ACCEPTED_202;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasEntry;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;

public class StripeRefundsResourceIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();

    private String stripeAccountId = "stripe_account_id";
    private String accountId = String.valueOf(nextLong());

    private DatabaseFixtures.TestAccount defaultTestAccount;
    private DatabaseFixtures.TestCharge defaultTestCharge;
    
    @BeforeEach
    void setUpStripe() {
        defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestAccount()
                .withPaymentProvider("stripe")
                .withCredentials(ImmutableMap.of("stripe_account_id", stripeAccountId))
                .withAccountId(Long.valueOf(accountId))
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

    @Test
    void shouldSuccessfullyRefund_usingChargeId() {
        var testChargeCreatedWithStripeChargeAPI = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withAmount(100L)
                .withTransactionId("ch_123")
                .withTestAccount(defaultTestAccount)
                .withChargeStatus(CAPTURED)
                .withPaymentProvider(STRIPE.getName())
                .insert();
        String platformAccountId = "stripe_platform_account_id";
        String externalChargeId = testChargeCreatedWithStripeChargeAPI.getExternalChargeId();
        long amount = 10L;
        app.getStripeMockClient().mockTransferSuccess();
        app.getStripeMockClient().mockRefund();

        ImmutableMap<String, Long> refundData = ImmutableMap.of("amount", amount, "refund_amount_available", testChargeCreatedWithStripeChargeAPI.getAmount());
        String refundPayload = new Gson().toJson(refundData);
        ValidatableResponse response = given().port(app.getLocalPort())
                .body(refundPayload)
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .post("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds"
                        .replace("{accountId}", accountId)
                        .replace("{chargeId}", externalChargeId))
                .then()
                .statusCode(ACCEPTED_202);

        List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(testChargeCreatedWithStripeChargeAPI.getExternalChargeId());
        assertThat(refundsFoundByChargeExternalId.size(), is(1));
        assertThat(refundsFoundByChargeExternalId.get(0).get("status"), is("REFUNDED"));
        assertThat(refundsFoundByChargeExternalId.get(0), hasEntry("charge_external_id", testChargeCreatedWithStripeChargeAPI.getExternalChargeId()));
        assertThat(refundsFoundByChargeExternalId.get(0), hasEntry("gateway_transaction_id", "re_1DRiccHj08j21DRiccHj08j2_test"));
        String refundId = response.extract().path("refund_id");
        
        app.getStripeWireMockServer().verify(postRequestedFor(urlEqualTo("/v1/refunds"))
                .withHeader("Idempotency-Key", equalTo("refund" + refundId))
                .withRequestBody(containing("charge=ch_123"))
                .withRequestBody(containing("amount=" + amount)));
        app.getStripeWireMockServer().verify(postRequestedFor(urlEqualTo("/v1/transfers"))
                .withHeader("Idempotency-Key", equalTo("transfer_in" + refundId))
                .withHeader("Stripe-Account", equalTo(stripeAccountId))
                .withRequestBody(containing("transfer_group=" + testChargeCreatedWithStripeChargeAPI.getExternalChargeId()))
                .withRequestBody(containing("destination=" + platformAccountId)));
    }

    @Test
    void shouldSuccessfullyRefund_usingPaymentIntentId() {
        String platformAccountId = "stripe_platform_account_id";
        String externalChargeId = defaultTestCharge.getExternalChargeId();
        long amount = 10L;
        app.getStripeMockClient().mockGetPaymentIntent(defaultTestCharge.getTransactionId());
        app.getStripeMockClient().mockTransferSuccess();
        app.getStripeMockClient().mockRefund();

        ImmutableMap<String, Long> refundData = ImmutableMap.of("amount", amount, "refund_amount_available", defaultTestCharge.getAmount());
        String refundPayload = new Gson().toJson(refundData);
        ValidatableResponse response = given().port(app.getLocalPort())
                .body(refundPayload)
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .post("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds"
                        .replace("{accountId}", accountId)
                        .replace("{chargeId}", externalChargeId))
                .then()
                .statusCode(ACCEPTED_202);

        List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
        assertThat(refundsFoundByChargeExternalId.size(), is(1));
        assertThat(refundsFoundByChargeExternalId.get(0).get("status"), is("REFUNDED"));
        String refundId = response.extract().path("refund_id");
        String paymentIntentUrl = "/v1/payment_intents/" + defaultTestCharge.getTransactionId() + "?expand%5B%5D=charges.data.balance_transaction";
        app.getStripeWireMockServer().verify(getRequestedFor(urlEqualTo(paymentIntentUrl)));
        app.getStripeWireMockServer().verify(postRequestedFor(urlEqualTo("/v1/refunds"))
                .withHeader("Idempotency-Key", equalTo("refund" + refundId))
                .withRequestBody(containing("charge=ch_123456"))
                .withRequestBody(containing("amount=" + amount)));
        app.getStripeWireMockServer().verify(postRequestedFor(urlEqualTo("/v1/transfers"))
                .withHeader("Idempotency-Key", equalTo("transfer_in" + refundId))
                .withHeader("Stripe-Account", equalTo(stripeAccountId))
                .withRequestBody(containing("transfer_group=" + defaultTestCharge.getExternalChargeId()))
                .withRequestBody(containing("destination=" + platformAccountId)));
    }
    
    @Test
    void stripeRefund_shouldResultInRefundErrorIfRefundFails() {
        String externalChargeId = defaultTestCharge.getExternalChargeId();
        long amount = 10L;
        app.getStripeMockClient().mockRefundError();

        ImmutableMap<String, Long> refundData = ImmutableMap.of("amount", amount, "refund_amount_available", defaultTestCharge.getAmount());
        String refundPayload = new Gson().toJson(refundData);
        given().port(app.getLocalPort())
                .body(refundPayload)
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .post("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds"
                        .replace("{accountId}", accountId)
                        .replace("{chargeId}", externalChargeId))
                .then()
                .statusCode(INTERNAL_SERVER_ERROR.getStatusCode());

        List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
        assertThat(refundsFoundByChargeExternalId.size(), is(1));
        assertThat(refundsFoundByChargeExternalId.get(0).get("status"), is("REFUND ERROR"));
    }
    
    @Test
    void stripeRefund_shouldResultInRefundErrorIfTransferFails() {
        String externalChargeId = defaultTestCharge.getExternalChargeId();
        long amount = 10L;

        app.getStripeMockClient().mockRefund();
        app.getStripeMockClient().mockTransferFailure();

        ImmutableMap<String, Long> refundData = ImmutableMap.of("amount", amount, "refund_amount_available", defaultTestCharge.getAmount());
        String refundPayload = new Gson().toJson(refundData);

        given().port(app.getLocalPort())
                .body(refundPayload)
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .post("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds"
                        .replace("{accountId}", accountId)
                        .replace("{chargeId}", externalChargeId))
                .then()
                .statusCode(INTERNAL_SERVER_ERROR.getStatusCode())
                .body("message", contains("Stripe refund response (error code: expired_card, error: Your card has expired.)"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));


        List<Map<String, Object>> refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(defaultTestCharge.getExternalChargeId());
        assertThat(refundsFoundByChargeExternalId.size(), is(1));
        assertThat(refundsFoundByChargeExternalId.get(0).get("status"), is("REFUND ERROR"));
    }
}
