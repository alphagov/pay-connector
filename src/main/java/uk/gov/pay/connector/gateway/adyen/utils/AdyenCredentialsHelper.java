package uk.gov.pay.connector.gateway.adyen.utils;

import uk.gov.pay.connector.gateway.model.request.GatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.AdyenCredentials;

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
