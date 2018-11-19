package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;

public class AuthorisationGatewayRequest extends BaseAuthorisationGatewayRequest {
    private AuthCardDetails authCardDetails;
    
    public AuthorisationGatewayRequest(ChargeEntity charge, AuthCardDetails authCardDetails) {
        super(charge);
        this.authCardDetails = authCardDetails;
    }

    public AuthCardDetails getAuthCardDetails() {
        return authCardDetails;
    }

    public static AuthorisationGatewayRequest valueOf(ChargeEntity charge, AuthCardDetails authCardDetails) {
        return new AuthorisationGatewayRequest(charge, authCardDetails);
    }
    
}
