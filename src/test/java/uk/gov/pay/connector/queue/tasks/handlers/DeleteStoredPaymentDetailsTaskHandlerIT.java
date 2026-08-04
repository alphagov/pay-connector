package uk.gov.pay.connector.queue.tasks.handlers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.util.AddPaymentInstrumentParams.AddPaymentInstrumentParamsBuilder.anAddPaymentInstrumentParams;

class DeleteStoredPaymentDetailsTaskHandlerIT {

    private static final String AGREEMENT_EXTERNAL_ID = "agreement-external-id-123";
    private static final String PAYMENT_INSTRUMENT_EXTERNAL_ID = "payment-instrument-ext-id-123";
    private static final String STORED_PAYMENT_METHOD_ID = "stored-payment-method-id-123";

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    
    private DeleteStoredPaymentDetailsTaskHandler deleteStoredPaymentDetailsTaskHandler;

    @BeforeEach
    void setUp() {
        deleteStoredPaymentDetailsTaskHandler = app.getInstanceFromGuiceContainer(DeleteStoredPaymentDetailsTaskHandler.class);
    }

    @Test
    void shouldDeleteStoredPaymentMethodOnAdyen() throws Exception {
        createAgreementAndPaymentInstrument();

        app.getAdyenWireMockServer().stubFor(delete(urlPathEqualTo("/storedPaymentMethods/" + STORED_PAYMENT_METHOD_ID))
                .withQueryParam("merchantAccount", equalTo("adyen-test-merchant-account-id"))
                .withQueryParam("shopperReference", equalTo(AGREEMENT_EXTERNAL_ID))
                .willReturn(aResponse().withStatus(204)));

        deleteStoredPaymentDetailsTaskHandler.process(AGREEMENT_EXTERNAL_ID, PAYMENT_INSTRUMENT_EXTERNAL_ID);

        app.getAdyenWireMockServer().verify(deleteRequestedFor(urlPathEqualTo("/storedPaymentMethods/" + STORED_PAYMENT_METHOD_ID))
                .withHeader("X-API-Key", equalTo("adyen-test-company-api-key"))
                .withQueryParam("merchantAccount", equalTo("adyen-test-merchant-account-id"))
                .withQueryParam("shopperReference", equalTo(AGREEMENT_EXTERNAL_ID)));
    }

    @Test
    void shouldThrowExceptionWhenDeleteStoredPaymentMethodFails() {
        createAgreementAndPaymentInstrument();

        app.getAdyenWireMockServer().stubFor(delete(urlPathEqualTo("/storedPaymentMethods/" + STORED_PAYMENT_METHOD_ID))
                .withQueryParam("merchantAccount", equalTo("adyen-test-merchant-account-id"))
                .withQueryParam("shopperReference", equalTo(AGREEMENT_EXTERNAL_ID))
                .willReturn(aResponse().withStatus(500).withBody("{\"error\":\"gateway error\"}")));

        assertThrows(GatewayException.GatewayErrorException.class,
                () -> deleteStoredPaymentDetailsTaskHandler.process(AGREEMENT_EXTERNAL_ID, PAYMENT_INSTRUMENT_EXTERNAL_ID));
    }

    private void createAgreementAndPaymentInstrument() {
        DatabaseFixtures.TestAccount testAccount = app.getDatabaseFixtures().aTestAccount()
                .withPaymentProvider(ADYEN.getName())
                .withCredentials(Map.of(
                        "legal_entity_id", "a-legal-entity-id",
                        "store_id", "a-store-id",
                        "account_holder_id", "an-account-holder-id",
                        "balance_account_id", "a-balance-account-id"))
                .insert();

        app.getDatabaseFixtures().aTestAgreement()
                .withExternalId(AGREEMENT_EXTERNAL_ID)
                .withGatewayAccountId(testAccount.getAccountId())
                .insert();

        app.getDatabaseTestHelper().addPaymentInstrument(anAddPaymentInstrumentParams()
                .withPaymentInstrumentId(424242L)
                .withExternalPaymentInstrumentId(PAYMENT_INSTRUMENT_EXTERNAL_ID)
                .withAgreementExternalId(AGREEMENT_EXTERNAL_ID)
                .withRecurringAuthToken(Map.of("storedPaymentMethodId", STORED_PAYMENT_METHOD_ID))
                .build());
    }
}
