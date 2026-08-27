package uk.gov.pay.connector.gateway.util;

import jakarta.inject.Inject;
import uk.gov.pay.connector.app.ConnectorConfiguration;

public class ChargeFrontendUrlHelper {

    private final ConnectorConfiguration configuration;
    
    @Inject
    public ChargeFrontendUrlHelper(ConnectorConfiguration configuration) {
        this.configuration = configuration;
    }
    
    public String getFrontendUrlForCharge(String chargeExternalId) {
        return String.format("%s/card_details/%s", configuration.getLinks().getFrontendUrl(), chargeExternalId);
    }
    
    public String getAdyen3dsRequiredInFrontendUrlForCharge(String chargeExternalId) {
        return String.format("%s/card_details/%s/3ds_required_in/adyen", configuration.getLinks().getFrontendUrl(), chargeExternalId);
    }

}
