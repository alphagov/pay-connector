package uk.gov.pay.connector.gateway.adyen.api;

import com.adyen.service.balanceplatform.AccountHoldersApi;
import com.adyen.service.balanceplatform.BalanceAccountsApi;
import com.adyen.service.legalentitymanagement.BusinessLinesApi;
import com.adyen.service.legalentitymanagement.LegalEntitiesApi;
import com.adyen.service.legalentitymanagement.PciQuestionnairesApi;
import com.adyen.service.legalentitymanagement.TermsOfServiceApi;
import com.adyen.service.legalentitymanagement.TransferInstrumentsApi;
import com.adyen.service.management.AccountStoreLevelApi;
import com.adyen.service.management.PaymentMethodsMerchantLevelApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.app.adyen.ApiKeysFixture;
import uk.gov.pay.connector.app.adyen.BaseUrlsFixture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdyenApiFactoryTest {

    private AdyenGatewayConfig adyenGatewayConfig;

    @BeforeEach
    void setUp() {
        adyenGatewayConfig = mock(AdyenGatewayConfig.class);

        when(adyenGatewayConfig.getApiKeys()).thenReturn(ApiKeysFixture.someApiKeys().build());
        when(adyenGatewayConfig.getBaseUrls()).thenReturn(BaseUrlsFixture.someBaseUrls().build());
    }

    @Test
    void shouldCreateBalancePlatformApisWithCorrectKeys() {
        AdyenBalancePlatformApiFactory adyenBalancePlatformApiFactory = new AdyenBalancePlatformApiFactory(adyenGatewayConfig);

        AccountHoldersApi accountHoldersApi = adyenBalancePlatformApiFactory.getAccountHoldersApi();
        BalanceAccountsApi balanceAccountsApi = adyenBalancePlatformApiFactory.getBalanceAccountsApi();
        
        assertThat(accountHoldersApi.getClient().getConfig().getApiKey(), is("test-balance-platform-API-key"));
        assertThat(balanceAccountsApi.getClient().getConfig().getApiKey(), is("test-balance-platform-API-key"));
    }

    @Test
    void shouldCreateCompanyAccountApisWithCorrectKeys() {
       AdyenCompanyAccountApiFactory adyenCompanyAccountApiFactory = new AdyenCompanyAccountApiFactory(adyenGatewayConfig);

        AccountStoreLevelApi accountStoreLevelApi = adyenCompanyAccountApiFactory.getAccountStoreLevelApi();
        PaymentMethodsMerchantLevelApi paymentMethodsMerchantLevelApi = adyenCompanyAccountApiFactory.getPaymentMethodsMerchantLevelApi();

        assertThat(accountStoreLevelApi.getClient().getConfig().getApiKey(), is("test-company-account-API-key"));
        assertThat(paymentMethodsMerchantLevelApi.getClient().getConfig().getApiKey(), is("test-company-account-API-key"));
    }

    @Test
    void shouldCreateKycApisWithCorrectKeys() {
        AdyenKycApiFactory kycApiFactory = new AdyenKycApiFactory(adyenGatewayConfig);

        PciQuestionnairesApi pciQuestionnairesApi = kycApiFactory.getPciQuestionnairesApi();
        TermsOfServiceApi termsOfServiceApi = kycApiFactory.getTermsOfServiceApi();
        LegalEntitiesApi legalEntitiesApi = kycApiFactory.getLegalEntitiesApi();
        BusinessLinesApi businessLinesApi = kycApiFactory.getBusinessLinesApi();
        TransferInstrumentsApi transferInstrumentsApi = kycApiFactory.getTransferInstrumentsApi();

        assertThat(pciQuestionnairesApi.getClient().getConfig().getApiKey(), is("test-legal-entity-management-API-key"));
        assertThat(termsOfServiceApi.getClient().getConfig().getApiKey(), is("test-legal-entity-management-API-key"));
        assertThat(legalEntitiesApi.getClient().getConfig().getApiKey(), is("test-legal-entity-management-API-key"));
        assertThat(businessLinesApi.getClient().getConfig().getApiKey(), is("test-legal-entity-management-API-key"));
        assertThat(transferInstrumentsApi.getClient().getConfig().getApiKey(), is("test-legal-entity-management-API-key"));
    }
}
