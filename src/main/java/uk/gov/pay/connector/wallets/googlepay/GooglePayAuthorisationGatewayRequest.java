package uk.gov.pay.connector.wallets.googlepay;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.googlepay.api.AdyenGooglePayAuthRequest;
import uk.gov.pay.connector.wallets.googlepay.api.GooglePayAuthRequest;
import uk.gov.pay.connector.wallets.googlepay.api.GooglePayAuthorisationRequest;

public class GooglePayAuthorisationGatewayRequest extends AuthorisationGatewayRequest {
    private final GooglePayAuthorisationRequest googlePayAuthorisationRequest;

    public GooglePayAuthorisationGatewayRequest(ChargeEntity charge, GooglePayAuthorisationRequest googlePayAuthRequest) {
        super(charge);
        this.googlePayAuthorisationRequest = googlePayAuthRequest;
    }

    public GooglePayAuthRequest getGooglePayAuthRequest() {
        return (GooglePayAuthRequest) getGooglePayAuthorisationRequest();
    }

    public AdyenGooglePayAuthRequest getAdyenGooglePayAuthRequest() {
        return (AdyenGooglePayAuthRequest) getGooglePayAuthorisationRequest();
    }

    private GooglePayAuthorisationRequest getGooglePayAuthorisationRequest() {
        return googlePayAuthorisationRequest;
    }

    public static GooglePayAuthorisationGatewayRequest valueOf(ChargeEntity charge, GooglePayAuthorisationRequest googlePayAuthRequest) {
        return new GooglePayAuthorisationGatewayRequest(charge, googlePayAuthRequest);
    }
}
