package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;

public class CardAuthorisationGatewayRequest extends AuthorisationGatewayRequest {
    private AuthCardDetails authCardDetails;
    
    public CardAuthorisationGatewayRequest(ChargeEntity charge, AuthCardDetails authCardDetails) {
        super(charge);
        this.authCardDetails = authCardDetails;
    }

    public AuthCardDetails getAuthCardDetails() {
        return authCardDetails;
    }

    public static CardAuthorisationGatewayRequest valueOf(ChargeEntity charge, AuthCardDetails authCardDetails) {
        return new CardAuthorisationGatewayRequest(charge, authCardDetails);
    }
    
}
