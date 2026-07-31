package uk.gov.pay.connector.gateway.model.request.records;

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

}
