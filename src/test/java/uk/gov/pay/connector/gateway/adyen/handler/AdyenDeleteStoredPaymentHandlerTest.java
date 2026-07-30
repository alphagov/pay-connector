package uk.gov.pay.connector.gateway.adyen.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.app.adyen.AdyenIds;
import uk.gov.pay.connector.app.adyen.ApiKeys;
import uk.gov.pay.connector.app.adyen.BaseUrls;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.model.request.DeleteStoredPaymentDetailsGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.GatewayClientDeleteRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.agreement.model.AgreementEntityFixture.anAgreementEntity;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntityFixture.aPaymentInstrumentEntity;

@ExtendWith(MockitoExtension.class)
class AdyenDeleteStoredPaymentHandlerTest {
    

    @Mock
    private ConnectorConfiguration connectorConfiguration;

    @Mock
    private AdyenGatewayConfig adyenGatewayConfig;

    @Mock
    private GatewayClient gatewayClient;

    private AdyenDeleteStoredPaymentHandler adyenDeleteStoredPaymentHandler;

    @BeforeEach
    void setUp() {
        when(connectorConfiguration.getAdyenGatewayConfig()).thenReturn(adyenGatewayConfig);
        adyenDeleteStoredPaymentHandler = new AdyenDeleteStoredPaymentHandler(gatewayClient, connectorConfiguration);
        stubDeleteStoredPaymentDetailsConfig();

    }

    @Test
    void shouldDeleteStoredPaymentDetails() throws Exception {
        DeleteStoredPaymentDetailsGatewayRequest request = createDeleteStoredPaymentDetailsGatewayRequest();

        adyenDeleteStoredPaymentHandler.deleteStoredPaymentDetails(request);

        ArgumentCaptor<GatewayClientDeleteRequest> captor = ArgumentCaptor.forClass(GatewayClientDeleteRequest.class);
        verify(gatewayClient).deleteRequestFor(captor.capture());

        GatewayClientDeleteRequest gatewayRequest = captor.getValue();
        assertThat(gatewayRequest.getUrl().toString(), is("https://example.com/test/version/storedPaymentMethods/storedPaymentMethodId-123"));
        assertThat(gatewayRequest.getHeaders(), hasEntry("X-API-Key", "test-api-key"));
        assertThat(gatewayRequest.getQueryParams(), hasEntry("merchantAccount", "merchant-account-test"));
        assertThat(gatewayRequest.getQueryParams(), hasEntry("shopperReference", "agreement-external-id-123"));
    }

    @Test
    void shouldThrowExceptionWhenDeleteStoredPaymentDetailsFails() throws Exception {
        DeleteStoredPaymentDetailsGatewayRequest request = createDeleteStoredPaymentDetailsGatewayRequest();
        doThrow(new GatewayException.GatewayErrorException("Non-success HTTP status code 500 from gateway", "{}", 500))
                .when(gatewayClient).deleteRequestFor(any(GatewayClientDeleteRequest.class));

        assertThrows(GatewayException.GatewayErrorException.class, () -> adyenDeleteStoredPaymentHandler.deleteStoredPaymentDetails(request));
    }

    private DeleteStoredPaymentDetailsGatewayRequest createDeleteStoredPaymentDetailsGatewayRequest() {
        PaymentInstrumentEntity paymentInstrumentEntity = aPaymentInstrumentEntity()
                .withExternalId("payment-instrument-ext-id-123")
                .withRecurringAuthToken(Map.of("storedPaymentMethodId", "storedPaymentMethodId-123"))
                .build();

        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withPaymentProvider(ADYEN.getName())
                .withCredentials(Map.of(
                        "legal_entity_id", "a-legal-entity-id",
                        "store_id", "a-store-id",
                        "account_holder_id", "an-account-holder-id",
                        "balance_account_id", "a-balance-account-id"))
                .build();

        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayName(ADYEN.getName())
                .withType(TEST)
                .withGatewayAccountCredentials(List.of(credentialsEntity))
                .build();

        var agreementEntity = anAgreementEntity()
                .withExternalId("agreement-external-id-123")
                .withGatewayAccount(gatewayAccountEntity)
                .withLive(false)
                .build();

        return DeleteStoredPaymentDetailsGatewayRequest.from(agreementEntity, paymentInstrumentEntity);
    }

    private void stubDeleteStoredPaymentDetailsConfig() {
        BaseUrls baseUrls = new BaseUrls(
                new BaseUrls.CheckoutUrls("https://example.com/test/version", "https://example.com/live/version"),
                new BaseUrls.BalancePlatformUrls("https://example.com/balance"),
                new BaseUrls.LegalEntityManagementUrls("https://example.com/legal-entity"),
                new BaseUrls.ManagementUrls("https://example.com/management")
        );
        ApiKeys apiKeys = new ApiKeys(
                new ApiKeys.CompanyAccountApiKeys("test-api-key", "live-api-key"),
                new ApiKeys.BalancePlatformApiKeys("balance-test-api-key", "balance-live-api-key"),
                new ApiKeys.LegalEntityManagementApiKeys("lem-test-api-key", "lem-live-api-key")
        );

        when(adyenGatewayConfig.getBaseUrls()).thenReturn(baseUrls);
        when(adyenGatewayConfig.getApiKeys()).thenReturn(apiKeys);
        when(adyenGatewayConfig.getMerchantAccountIds()).thenReturn(new AdyenIds("merchant-account-test", "merchant-account-live"));
    }

}
