package uk.gov.pay.connector.it.resources.adyen;

import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenCredentials.ADYEN_LEGAL_ENTITY_ID;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenCredentials.ADYEN_STORE_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomLong;

class AdyenRefundSubmitResourceIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();

    private static final String SERVICE_ID = "a-valid-service-id";
    private static final String TRANSACTION_ID = "991617895215577D";
    private static final String ADYEN_STORE = "STORE_ID_123";
    private static final Map<String, Object> validAdyenCredentials = Map.of(
            ADYEN_LEGAL_ENTITY_ID, "legal_entity_id",
            ADYEN_STORE_ID, ADYEN_STORE
    );

    private long accountId;
    private long credentialsId;
    private DatabaseFixtures.TestCharge testCharge;

    @BeforeEach
    void setUp() {
        accountId = randomLong();
        credentialsId = randomLong();

        var credentialParams = anAddGatewayAccountCredentialsParams()
                .withId(credentialsId)
                .withPaymentProvider(ADYEN.getName())
                .withGatewayAccountId(accountId)
                .withState(ACTIVE)
                .withCredentials(validAdyenCredentials)
                .build();

        var testAccount = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestAccount()
                .withAccountId(accountId)
                .withGatewayAccountCredentials(List.of(credentialParams))
                .withServiceId(SERVICE_ID)
                .withType(TEST)
                .insert();

        testCharge = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withAmount(100L)
                .withTransactionId(TRANSACTION_ID)
                .withTestAccount(testAccount)
                .withChargeStatus(CAPTURED)
                .withPaymentProvider(ADYEN.getName())
                .withGatewayCredentialId(credentialsId)
                .insert();
    }

    @Test
    void shouldSubmitRefundToAdyen() {
        long refundAmount = 50L;
        var refundPspReference = "883617895215577D";
        app.getAdyenCheckoutMockClient().mockRefundSuccess(refundPspReference, testCharge.getTransactionId());

        ValidatableResponse response = postRefundFor(accountId, testCharge.getExternalChargeId(), refundAmount, testCharge.getAmount())
                .statusCode(202)
                .body("refund_id", is(notNullValue()))
                .body("amount", is((int) refundAmount))
                .body("status", is("submitted"))
                .body("created_date", is(notNullValue()));
        String refundId = response.extract().path("refund_id");

        String paymentUrl = format("https://localhost:%s/v1/api/accounts/%s/charges/%s",
                app.getLocalPort(), accountId, testCharge.getExternalChargeId());
        response.body("_links.self.href", is(paymentUrl + "/refunds/" + refundId))
                .body("_links.payment.href", is(paymentUrl));

        var refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(testCharge.getExternalChargeId());
        assertThat(refundsFoundByChargeExternalId.size(), is(1));
        assertThat(refundsFoundByChargeExternalId.getFirst(), hasEntry("charge_external_id", testCharge.getExternalChargeId()));
        assertThat(refundsFoundByChargeExternalId.getFirst(), hasEntry("gateway_transaction_id", refundPspReference));
        assertThat(refundsFoundByChargeExternalId.getFirst(), hasEntry("status", "REFUND SUBMITTED"));

        var refundsStatusHistory = app.getDatabaseTestHelper().getRefundsHistoryByChargeExternalId(testCharge.getExternalChargeId())
                .stream()
                .map(x -> x.get("status").toString())
                .collect(Collectors.toList());
        assertThat(refundsStatusHistory.size(), is(2));
        assertThat(refundsStatusHistory, containsInAnyOrder("REFUND SUBMITTED", "CREATED"));

        app.getAdyenWireMockServer().verify(postRequestedFor(urlEqualTo("/payments/" + testCharge.getTransactionId() + "/refunds"))
                .withHeader("X-API-Key", equalTo("adyen-test-company-api-key"))
                .withRequestBody(matchingJsonPath("$.amount.currency", equalTo("GBP")))
                .withRequestBody(matchingJsonPath("$.amount.value", equalTo(String.valueOf(refundAmount))))
                .withRequestBody(matchingJsonPath("$.merchantAccount", equalTo("adyen-test-merchant-account-id")))
                .withRequestBody(matchingJsonPath("$.reference", equalTo(refundId)))
                .withRequestBody(matchingJsonPath("$.store", equalTo(ADYEN_STORE))));
    }

    private ValidatableResponse postRefundFor(long accountId, String chargeId, Long refundAmount, Long refundAmountAvailable) {
        return app.givenSetup()
                .body(toJson(Map.of(
                        "amount", refundAmount,
                        "refund_amount_available", refundAmountAvailable
                )))
                .post("/v1/api/accounts/{accountId}/charges/{chargeId}/refunds"
                        .replace("{accountId}", String.valueOf(accountId))
                        .replace("{chargeId}", chargeId))
                .then();
    }
}
