package uk.gov.pay.connector.it.resources.stripe;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.commons.model.ErrorIdentifier;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.rules.StripeMockClient;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static io.restassured.RestAssured.given;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.eclipse.jetty.http.HttpStatus.ACCEPTED_202;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class StripeRefundIT extends ChargingITestBase {
    private String stripeAccountId = "stripe_account_id";
    private String accountId = "555";

    private StripeMockClient stripeMockClient = new StripeMockClient();
    private DatabaseFixtures.TestAccount defaultTestAccount;
    private DatabaseFixtures.TestCharge defaultTestCharge;

    public StripeRefundIT() {
        super("stripe");
    }

    @Before
    public void setUp() {
        super.setUp();
        defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withPaymentProvider("stripe")
                .withCredentials(ImmutableMap.of("stripe_account_id", stripeAccountId))
                .withAccountId(Long.valueOf(accountId))
                .insert();

        defaultTestCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withAmount(100L)
                .withTransactionId("charge_transaction_id")
                .withTestAccount(defaultTestAccount)
                .withChargeStatus(CAPTURED)
                .insert();

        stripeMockClient.mockGetPaymentIntent(defaultTestCharge.getTransactionId());
    }
    
    @Test
    public void shouldSuccessfullyRefund() {
        String platformAccountId = "stripe_platform_account_id";
        String externalChargeId = defaultTestCharge.getExternalChargeId();
        long amount = 10L;
        stripeMockClient.mockGetPaymentIntent(defaultTestCharge.getTransactionId());
        stripeMockClient.mockTransferSuccess(null);
        stripeMockClient.mockRefund();

        ImmutableMap<String, Long> refundData = ImmutableMap.of("amount", amount, "refund_amount_available", defaultTestCharge.getAmount());
        String refundPayload = new Gson().toJson(refundData);
        ValidatableResponse response = given().port(testContext.getPort())
                .body(refundPayload)
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .post("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds"
                        .replace("{accountId}", accountId)
                        .replace("{chargeId}", externalChargeId))
                .then()
                .statusCode(ACCEPTED_202);

        List<Map<String, Object>> refundsFoundByChargeId = databaseTestHelper.getRefundsByChargeId(defaultTestCharge.getChargeId());
        assertThat(refundsFoundByChargeId.size(), is(1));
        assertThat(refundsFoundByChargeId.get(0).get("status"), is("REFUNDED"));
        String refundId = response.extract().path("refund_id");
        verify(postRequestedFor(urlEqualTo("/v1/payment_intents/" + defaultTestCharge.getTransactionId())));
        verify(postRequestedFor(urlEqualTo("/v1/refunds"))
                .withHeader("Idempotency-Key", equalTo("refund" + refundId))
                .withRequestBody(containing("charge=ch_123456"))
                .withRequestBody(containing("amount=" + amount)));
        verify(postRequestedFor(urlEqualTo("/v1/transfers"))
                .withHeader("Idempotency-Key", equalTo("transfer_in" + refundId))
                .withHeader("Stripe-Account", equalTo(stripeAccountId))
                .withRequestBody(containing("transfer_group=" + defaultTestCharge.getExternalChargeId()))
                .withRequestBody(containing("destination=" + platformAccountId)));
    }
    
    @Test
    public void stripeRefund_shouldResultInRefundErrorIfRefundFails() {
        String externalChargeId = defaultTestCharge.getExternalChargeId();
        long amount = 10L;
        stripeMockClient.mockTransferSuccess(null);
        stripeMockClient.mockRefundError();
        stripeMockClient.mockTransferReversal("transfer_id");

        ImmutableMap<String, Long> refundData = ImmutableMap.of("amount", amount, "refund_amount_available", defaultTestCharge.getAmount());
        String refundPayload = new Gson().toJson(refundData);
        given().port(testContext.getPort())
                .body(refundPayload)
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .post("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds"
                        .replace("{accountId}", accountId)
                        .replace("{chargeId}", externalChargeId))
                .then()
                .statusCode(INTERNAL_SERVER_ERROR.getStatusCode());

        List<Map<String, Object>> refundsFoundByChargeId = databaseTestHelper.getRefundsByChargeId(defaultTestCharge.getChargeId());
        assertThat(refundsFoundByChargeId.size(), is(1));
        assertThat(refundsFoundByChargeId.get(0).get("status"), is("REFUND ERROR"));
    }
    
    @Test
    public void stripeRefund_shouldResultInRefundErrorIfTransferFails() {
        String externalChargeId = defaultTestCharge.getExternalChargeId();
        long amount = 10L;
        
        stripeMockClient.mockTransferFailure();

        ImmutableMap<String, Long> refundData = ImmutableMap.of("amount", amount, "refund_amount_available", defaultTestCharge.getAmount());
        String refundPayload = new Gson().toJson(refundData);

        given().port(testContext.getPort())
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


        List<Map<String, Object>> refundsFoundByChargeId = databaseTestHelper.getRefundsByChargeId(defaultTestCharge.getChargeId());
        assertThat(refundsFoundByChargeId.size(), is(1));
        assertThat(refundsFoundByChargeId.get(0).get("status"), is("REFUND ERROR"));
    }
}
