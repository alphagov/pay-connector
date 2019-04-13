package uk.gov.pay.connector.it.resources.stripe;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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
import static org.eclipse.jetty.http.HttpStatus.ACCEPTED_202;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class StripeRefundITest extends ChargingITestBase {
    private String stripeAccountId = "stripe_account_id";
    private String accountId = "555";

    private StripeMockClient stripeMockClient = new StripeMockClient();
    private DatabaseFixtures.TestAccount defaultTestAccount;
    private DatabaseFixtures.TestCharge defaultTestCharge;

    public StripeRefundITest() {
        super("stripe");
    }

    @Before
    public void setup() {
        super.setup();
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


    }

    @Test
    public void stripeRefund() {
        String externalChargeId = defaultTestCharge.getExternalChargeId();
        long amount = 10L;
        
        stripeMockClient.mockCancelCharge();

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

        Long chargeId = databaseTestHelper.getChargeIdByExternalId(externalChargeId);
        assertEquals(databaseTestHelper.getChargeStatus(chargeId), CAPTURED.getValue());

        String refundId = response.extract().path("refund_id");

        verify(postRequestedFor(urlEqualTo("/v1/refunds"))
                .withHeader("Idempotency-Key", equalTo(refundId))
                .withRequestBody(containing("charge=" + defaultTestCharge.getTransactionId()))
                .withRequestBody(containing("amount=" + amount))
                .withRequestBody(containing("reverse_transfer=true"))
                .withRequestBody(containing("refund_application_fee=true")));
    }
}
