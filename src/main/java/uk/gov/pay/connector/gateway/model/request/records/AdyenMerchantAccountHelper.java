package uk.gov.pay.connector.gateway.model.request.records;

import jakarta.inject.Inject;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

public class AdyenMerchantAccountHelper {

    private final ConnectorConfiguration configuration;

    @Inject
    public AdyenMerchantAccountHelper(ConnectorConfiguration config) {
        this.configuration = config;
    }

    public String getMerchantAccount(GatewayAccountEntity gatewayAccountEntity) {
        return getMerchantAccount(gatewayAccountEntity.isLive());
    }

    public String getMerchantAccount(boolean live) {
        return live
                ? configuration.getAdyenGatewayConfig().getMerchantAccountIds().live()
                : configuration.getAdyenGatewayConfig().getMerchantAccountIds().test();
    }

}
