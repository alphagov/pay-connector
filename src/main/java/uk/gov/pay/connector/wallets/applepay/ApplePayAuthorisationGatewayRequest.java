package uk.gov.pay.connector.wallets.applepay;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;

public class ApplePayAuthorisationGatewayRequest extends AuthorisationGatewayRequest {
    private ApplePayAuthRequest applePayAuthRequest;

    public ApplePayAuthorisationGatewayRequest(ChargeEntity charge, ApplePayAuthRequest applePayAuthRequest) {
        super(charge);
        this.applePayAuthRequest = applePayAuthRequest;
    }

    public ApplePayAuthRequest getApplePayAuthRequest() {
        return applePayAuthRequest;
    }

    public static ApplePayAuthorisationGatewayRequest valueOf(ChargeEntity charge, ApplePayAuthRequest applePayAuthRequest) {
        return new ApplePayAuthorisationGatewayRequest(charge, applePayAuthRequest);
    }
}
