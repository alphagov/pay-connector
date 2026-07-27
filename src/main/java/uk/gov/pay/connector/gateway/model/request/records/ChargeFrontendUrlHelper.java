package uk.gov.pay.connector.gateway.model.request.records;

import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.gateway.model.request.GatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.AdyenCredentials;

public class ChargeFrontendUrlHelper {

    private final ConnectorConfiguration configuration;
    
    public ChargeFrontendUrlHelper(ConnectorConfiguration configuration) {
        this.configuration = configuration;
    }
    
    public String getFrontendUrlForCharge(String chargeExternalId) {
        return String.format("%s/card_details/%s", configuration.getLinks().getFrontendUrl(), chargeExternalId);
    }

}
