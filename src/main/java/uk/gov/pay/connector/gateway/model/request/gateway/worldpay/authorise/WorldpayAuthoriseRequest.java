package uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise;

import org.jspecify.annotations.NullMarked;
import uk.gov.pay.connector.gateway.model.request.gateway.GatewayAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.WorldpayRequest;

@NullMarked
public interface WorldpayAuthoriseRequest extends GatewayAuthoriseRequest, WorldpayRequest {

    String amountInPence();
    String description();
    
    default boolean is3dsFlexRequest() {
        return false;
    }
    
    default boolean storesTokenisedCredentials() {
        return false;
    }
}
