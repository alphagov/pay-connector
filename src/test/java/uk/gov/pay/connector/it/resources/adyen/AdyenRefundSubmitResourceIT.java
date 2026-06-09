package uk.gov.pay.connector.it.resources.adyen;

import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.util.List;
import java.util.Map;

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
    private static final String TRANSACTION_ID = "PSP-REF-123";
    private static final String ADYEN_STORE = "store-id-123";
    private static final String REFUND_SUBMITTED = "REFUND SUBMITTED";
    private static final String REFUND_ERROR = "REFUND ERROR";
    private static final Map<String, Object> validAdyenCredentials = Map.of(
            ADYEN_LEGAL_ENTITY_ID, "legal_entity_id",
            ADYEN_STORE_ID, ADYEN_STORE
    );

    private long accountId;
    private long credentialsId;
    private DatabaseFixtures.TestCharge testCharge;

    @BeforeEach
    void setUp() {
        app.getAdyenWireMockServer().resetAll();

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
                .withAmount(1000L)
                .withTransactionId(TRANSACTION_ID)
                .withTestAccount(testAccount)
                .withChargeStatus(CAPTURED)
                .withPaymentProvider(ADYEN.getName())
                .withGatewayCredentialId(credentialsId)
                .insert();
    }

    @Test
    void shouldSubmitSuccessfulFullRefundToAdyen() {
        long refundAmount = 1000L;
        var refundPspReference = "REFUND-PSP-REF-123";
        app.getAdyenCheckoutMockClient().mockRefundSuccess(refundPspReference, testCharge.getTransactionId());

        ValidatableResponse response = submitRefund(accountId, testCharge.getExternalChargeId(), refundAmount, testCharge.getAmount());
        String refundId = response.extract().path("refund_id");

        String paymentUrl = format("https://localhost:%s/v1/api/accounts/%s/charges/%s",
                app.getLocalPort(), accountId, testCharge.getExternalChargeId());
        response.body("_links.self.href", is(paymentUrl + "/refunds/" + refundId))
                .body("_links.payment.href", is(paymentUrl));

        assertRefundPersistedWithStatusAndGatewayTransactionId(refundPspReference, REFUND_SUBMITTED);
        verifyAdyenRefundRequest(refundAmount, refundId);
    }

    @Test
    void shouldSubmitSuccessfulPartialRefundToAdyen() {
        long refundAmount = 100L;
        var refundPspReference = "REFUND-PSP-REF-123";
        app.getAdyenCheckoutMockClient().mockRefundSuccess(refundPspReference, testCharge.getTransactionId());

        ValidatableResponse response = submitRefund(accountId, testCharge.getExternalChargeId(), refundAmount, testCharge.getAmount());
        String refundId = response.extract().path("refund_id");

        assertRefundPersistedWithStatusAndGatewayTransactionId(refundPspReference, REFUND_SUBMITTED);
        verifyAdyenRefundRequest(refundAmount, refundId);
    }

    @Test
    void shouldSetRefundErrorWhenAdyenReturnsNon2xxResponse() {
        long refundAmount = 100L;
        app.getAdyenCheckoutMockClient().mockRefundError(testCharge.getTransactionId());

        postRefundFor(accountId, testCharge.getExternalChargeId(), refundAmount, testCharge.getAmount())
                .statusCode(500)
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));

        assertRefundPersistedWithStatusAndGatewayTransactionId(null, REFUND_ERROR);
    }

    @Test
    void shouldSetRefundErrorWhenAdyenReturnsUnexpectedGatewayError() {
        long refundAmount = 100L;
        app.getAdyenCheckoutMockClient().mockError("/payments/" + testCharge.getTransactionId() + "/refunds");

        postRefundFor(accountId, testCharge.getExternalChargeId(), refundAmount, testCharge.getAmount())
                .statusCode(500)
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));

        assertRefundPersistedWithStatusAndGatewayTransactionId(null, REFUND_ERROR);
    }

    private ValidatableResponse submitRefund(long accountId, String chargeId, long refundAmount, long refundAmountAvailable) {
        return postRefundFor(accountId, chargeId, refundAmount, refundAmountAvailable)
                .statusCode(202)
                .body("refund_id", is(notNullValue()))
                .body("amount", is((int) refundAmount))
                .body("status", is("submitted"))
                .body("created_date", is(notNullValue()));
    }

    private void verifyAdyenRefundRequest(long refundAmount, String refundId) {
        app.getAdyenWireMockServer().verify(postRequestedFor(urlEqualTo("/payments/" + testCharge.getTransactionId() + "/refunds"))
                .withHeader("X-API-Key", equalTo("adyen-test-company-api-key"))
                .withHeader("Idempotency-Key", equalTo("refund-" + refundId))
                .withRequestBody(matchingJsonPath("$.amount.currency", equalTo("GBP")))
                .withRequestBody(matchingJsonPath("$.amount.value", equalTo(String.valueOf(refundAmount))))
                .withRequestBody(matchingJsonPath("$.merchantAccount", equalTo("adyen-test-merchant-account-id")))
                .withRequestBody(matchingJsonPath("$.reference", equalTo(refundId)))
                .withRequestBody(matchingJsonPath("$.store", equalTo(ADYEN_STORE))));
    }

    private void assertRefundPersistedWithStatusAndGatewayTransactionId(String gatewayTransactionId, String status) {
        var refundsFoundByChargeExternalId = app.getDatabaseTestHelper().getRefundsByChargeExternalId(testCharge.getExternalChargeId());
        assertThat(refundsFoundByChargeExternalId.size(), is(1));
        assertThat(refundsFoundByChargeExternalId.getFirst(), hasEntry("charge_external_id", testCharge.getExternalChargeId()));
        assertThat(refundsFoundByChargeExternalId.getFirst(), hasEntry("gateway_transaction_id", gatewayTransactionId));
        assertThat(refundsFoundByChargeExternalId.getFirst(), hasEntry("status", status));

        var refundsStatusHistory = app.getDatabaseTestHelper().getRefundsHistoryByChargeExternalId(testCharge.getExternalChargeId())
                .stream()
                .map(x -> x.get("status").toString())
                .toList();
        assertThat(refundsStatusHistory.size(), is(2));
        assertThat(refundsStatusHistory, containsInAnyOrder(status, "CREATED"));
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
