package uk.gov.pay.connector.gateway.adyen.api;

import com.adyen.Client;
import com.adyen.service.balanceplatform.AccountHoldersApi;
import com.adyen.service.balanceplatform.BalanceAccountsApi;
import jakarta.inject.Inject;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;

import java.util.Objects;

import static com.adyen.enums.Environment.TEST;
import static org.apache.commons.lang3.StringUtils.isBlank;

public class AdyenBalancePlatformApiFactory {
    
    private final AccountHoldersApi accountHoldersApi;
    private final BalanceAccountsApi balanceAccountsApi;

    @Inject
    public AdyenBalancePlatformApiFactory(AdyenGatewayConfig adyenGatewayConfig) {
        Client balancePlatformClient = new Client(adyenGatewayConfig.getApiKeys().balancePlatform().test(), TEST);

        accountHoldersApi = createAccountHoldersApi(balancePlatformClient, adyenGatewayConfig);
        balanceAccountsApi = createBalanceAccountsApi(balancePlatformClient, adyenGatewayConfig);
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


    public AccountHoldersApi getAccountHoldersApi() {
        return accountHoldersApi;
    }

    public BalanceAccountsApi getBalanceAccountsApi() {
        return balanceAccountsApi;
    }
}
