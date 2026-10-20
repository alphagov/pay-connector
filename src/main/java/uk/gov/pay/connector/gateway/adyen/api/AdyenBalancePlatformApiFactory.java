package uk.gov.pay.connector.gateway.adyen.api;

import com.adyen.Client;
import com.adyen.service.balanceplatform.AccountHoldersApi;
import com.adyen.service.balanceplatform.BalanceAccountsApi;
import com.adyen.service.balanceplatform.CustomPayoutSchedulesSweepsApi;
import jakarta.inject.Inject;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;

import java.util.Objects;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.gateway.adyen.api.AdyenHttpClientFactory.createAdyenHttpClientForTestEnvironment;

public class AdyenBalancePlatformApiFactory {

    private final AccountHoldersApi accountHoldersApi;
    private final BalanceAccountsApi balanceAccountsApi;
    private final CustomPayoutSchedulesSweepsApi customPayoutSchedulesSweepsApi;

    @Inject
    public AdyenBalancePlatformApiFactory(AdyenGatewayConfig adyenGatewayConfig) {
        Client balancePlatformClient = createAdyenHttpClientForTestEnvironment(adyenGatewayConfig.getApiKeys().balancePlatform().test());

        accountHoldersApi = createAccountHoldersApi(balancePlatformClient, adyenGatewayConfig);
        balanceAccountsApi = createBalanceAccountsApi(balancePlatformClient, adyenGatewayConfig);
        customPayoutSchedulesSweepsApi = createCustomPayoutSchedulesSweepsApi(balancePlatformClient, adyenGatewayConfig);
    }

    private AccountHoldersApi createAccountHoldersApi(Client client, AdyenGatewayConfig adyenGatewayConfig) {
        String balancePlatformBaseUrl = adyenGatewayConfig.getBaseUrls().balancePlatform().test();

        if (isBlank(balancePlatformBaseUrl)) {
            return new AccountHoldersApi(client);
        }
        return new AccountHoldersApi(client, balancePlatformBaseUrl);
    }

    private BalanceAccountsApi createBalanceAccountsApi(Client client, AdyenGatewayConfig adyenGatewayConfig) {
        String balancePlatformBaseUrl = adyenGatewayConfig.getBaseUrls().balancePlatform().test();

        if (Objects.isNull(balancePlatformBaseUrl)) {
            return new BalanceAccountsApi(client);
        }
        return new BalanceAccountsApi(client, balancePlatformBaseUrl);
    }

    private CustomPayoutSchedulesSweepsApi createCustomPayoutSchedulesSweepsApi(Client client, AdyenGatewayConfig adyenGatewayConfig) {
        String balancePlatformBaseUrl = adyenGatewayConfig.getBaseUrls().balancePlatform().test();

        if (Objects.isNull(balancePlatformBaseUrl)) {
            return new CustomPayoutSchedulesSweepsApi(client);
        }
        return new CustomPayoutSchedulesSweepsApi(client, balancePlatformBaseUrl);
    }

    public AccountHoldersApi getAccountHoldersApi() {
        return accountHoldersApi;
    }

    public BalanceAccountsApi getBalanceAccountsApi() {
        return balanceAccountsApi;
    }

    public CustomPayoutSchedulesSweepsApi getCustomPayoutSchedulesSweepsApi() {
        return customPayoutSchedulesSweepsApi;
    }
}
