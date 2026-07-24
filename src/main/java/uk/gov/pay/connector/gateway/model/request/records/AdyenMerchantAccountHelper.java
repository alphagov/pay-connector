package uk.gov.pay.connector.gateway.model.request.records;

import jakarta.inject.Inject;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.gateway.model.request.GatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccount;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

public class AdyenMerchantAccountHelper {

    private final ConnectorConfiguration configuration;

    @Inject
    public AdyenMerchantAccountHelper(ConnectorConfiguration config) {
        this.configuration = config;
    }
    
    public String getMerchantAccount(GatewayAccountEntity gatewayAccountEntity) {
        String merchantAccountId;
        if (gatewayAccountEntity.isLive()) {
            merchantAccountId = configuration.getAdyenGatewayConfig().getMerchantAccountIds().live();
        } else {
            merchantAccountId = configuration.getAdyenGatewayConfig().getMerchantAccountIds().test();
        }
        return merchantAccountId;
    }
    
}
