package uk.gov.pay.connector.gateway.adyen.api;

import com.adyen.Client;
import com.adyen.service.management.AccountStoreLevelApi;
import com.adyen.service.management.PaymentMethodsMerchantLevelApi;
import com.google.inject.Inject;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;

import java.util.Objects;

import static uk.gov.pay.connector.gateway.adyen.api.AdyenHttpClientFactory.createAdyenHttpClientForTestEnvironment;

public class AdyenCompanyAccountApiFactory {
    
    private final AccountStoreLevelApi accountStoreLevelApi;
    private final PaymentMethodsMerchantLevelApi paymentMethodsMerchantLevelApi;
    
    @Inject
    public AdyenCompanyAccountApiFactory(AdyenGatewayConfig adyenGatewayConfig) {
        Client companyAccountClient = createAdyenHttpClientForTestEnvironment(adyenGatewayConfig.getApiKeys().companyAccount().test());
        
        accountStoreLevelApi = createAccountStoreLevelApi(companyAccountClient, adyenGatewayConfig);
        paymentMethodsMerchantLevelApi = createPaymentMethodsMerchantLevelApi(companyAccountClient, adyenGatewayConfig);
    }

    private AccountStoreLevelApi createAccountStoreLevelApi(Client client, AdyenGatewayConfig adyenGatewayConfig) {
        String managementBaseUrl = adyenGatewayConfig.getBaseUrls().management().test();

        if (Objects.isNull(managementBaseUrl)) {
            return new AccountStoreLevelApi(client);
        }
        return new AccountStoreLevelApi(client, managementBaseUrl);
    }

    private PaymentMethodsMerchantLevelApi createPaymentMethodsMerchantLevelApi(Client client, AdyenGatewayConfig adyenGatewayConfig) {
        String managementBaseUrl = adyenGatewayConfig.getBaseUrls().management().test();

        if (Objects.isNull(managementBaseUrl)) {
            return new PaymentMethodsMerchantLevelApi(client);
        }
        return new PaymentMethodsMerchantLevelApi(client, managementBaseUrl);
    }

    public AccountStoreLevelApi getAccountStoreLevelApi() {
        return accountStoreLevelApi;
    }

    public PaymentMethodsMerchantLevelApi getPaymentMethodsMerchantLevelApi() {
        return paymentMethodsMerchantLevelApi;
    }
}
