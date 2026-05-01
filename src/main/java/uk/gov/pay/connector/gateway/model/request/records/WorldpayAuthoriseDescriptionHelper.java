package uk.gov.pay.connector.gateway.model.request.records;

import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;

public class WorldpayAuthoriseDescriptionHelper {
    public String getDescription(CardAuthorisationGatewayRequest cardAuthorisationGatewayRequest){
        
        return cardAuthorisationGatewayRequest.getGatewayAccount().isSendReferenceToGateway() ? 
                cardAuthorisationGatewayRequest.getReference().toString() : cardAuthorisationGatewayRequest.getDescription(); 
    }
}
