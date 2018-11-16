package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;

public class AuthorisationCardGatewayRequest extends AuthorisationGatewayRequest {
    private AuthCardDetails authCardDetails;
    
    public AuthorisationCardGatewayRequest(ChargeEntity charge, AuthCardDetails authCardDetails) {
        super(charge);
        this.authCardDetails = authCardDetails;
    }

    public AuthCardDetails getAuthCardDetails() {
        return authCardDetails;
    }

    public static AuthorisationCardGatewayRequest valueOf(ChargeEntity charge, AuthCardDetails authCardDetails) {
        return new AuthorisationCardGatewayRequest(charge, authCardDetails);
    }
    
}
