package uk.gov.pay.connector.wallets.googlepay;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.googlepay.api.AdyenGooglePayAuthRequest;
import uk.gov.pay.connector.wallets.googlepay.api.GooglePayAuthRequest;

public class GooglePayAuthorisationGatewayRequest extends AuthorisationGatewayRequest {
    private GooglePayAuthRequest googlePayAuthRequest;
    private AdyenGooglePayAuthRequest adyenGooglePayAuthRequest;

    public GooglePayAuthorisationGatewayRequest(ChargeEntity charge, GooglePayAuthRequest googlePayAuthRequest) {
        super(charge);
        this.googlePayAuthRequest = googlePayAuthRequest;
    }

    public GooglePayAuthorisationGatewayRequest(ChargeEntity charge, AdyenGooglePayAuthRequest googlePayAuthRequest) {
        super(charge);
        this.adyenGooglePayAuthRequest = googlePayAuthRequest;
    }

    public GooglePayAuthRequest getGooglePayAuthRequest() {
        return googlePayAuthRequest;
    }
    
    public AdyenGooglePayAuthRequest getAdyenGooglePayAuthRequest() {
        return adyenGooglePayAuthRequest;
    }

    public static GooglePayAuthorisationGatewayRequest valueOf(ChargeEntity charge, GooglePayAuthRequest googlePayAuthRequest) {
        return new GooglePayAuthorisationGatewayRequest(charge, googlePayAuthRequest);
    }

    public static GooglePayAuthorisationGatewayRequest valueOf(ChargeEntity charge, AdyenGooglePayAuthRequest googlePayAuthRequest) {
        return new GooglePayAuthorisationGatewayRequest(charge, googlePayAuthRequest);
    }
}
