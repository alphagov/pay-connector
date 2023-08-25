package uk.gov.pay.connector.wallets.model;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.googlepay.api.StripeGooglePayAuthRequest;

public class StripeGooglePayAuthorisationGatewayRequest extends AuthorisationGatewayRequest {
    
    private final String tokenId;

    public StripeGooglePayAuthorisationGatewayRequest(ChargeEntity charge, StripeGooglePayAuthRequest stripeGooglePayAuthRequest) {
        super(charge);
        this.tokenId = stripeGooglePayAuthRequest.getTokenId();
    }

    public String getTokenId() {
        return tokenId;
    }
}
