package uk.gov.pay.connector.gateway.model.request.records;

import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.GatewayRequest;
import uk.gov.pay.connector.gateway.util.AuthUtil;
import uk.gov.pay.connector.gatewayaccount.model.AdyenCredentials;
import uk.gov.service.payments.commons.model.AuthorisationMode;

public class AdyenCredentialsHelper {

    public String getStore(GatewayRequest gatewayRequest) {
        return getAdyenCredentials(gatewayRequest).storeId();
    }
    
    private AdyenCredentials getAdyenCredentials(GatewayRequest gatewayRequest){
        var gatewayCredentials = gatewayRequest.getGatewayCredentials();
        
        if (gatewayCredentials instanceof AdyenCredentials adyenCredentials) {
            return adyenCredentials;
        }

        throw new IllegalArgumentException("Expected provided GatewayCredentials to be of type AdyenCredentials");
    }

}
