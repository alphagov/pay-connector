package uk.gov.pay.connector.wallets.googlepay;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.googlepay.api.GooglePayAuthRequest;

public class GooglePayAuthorisationGatewayRequest extends AuthorisationGatewayRequest {
    private GooglePayAuthRequest googlePayAuthRequest;

    public GooglePayAuthorisationGatewayRequest(ChargeEntity charge, GooglePayAuthRequest googlePayAuthRequest) {
        super(charge);
        this.googlePayAuthRequest = googlePayAuthRequest;
    }

    public GooglePayAuthRequest getGooglePayAuthRequest() {
        return googlePayAuthRequest;
    }

    public static GooglePayAuthorisationGatewayRequest valueOf(ChargeEntity charge, GooglePayAuthRequest googlePayAuthRequest) {
        return new GooglePayAuthorisationGatewayRequest(charge, googlePayAuthRequest);
    }
}
