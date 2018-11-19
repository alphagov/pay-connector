package uk.gov.pay.connector.applepay;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.request.BaseAuthorisationGatewayRequest;

public class AuthorisationApplePayGatewayRequest extends BaseAuthorisationGatewayRequest {
    private AppleDecryptedPaymentData applePaymentData;

    public AuthorisationApplePayGatewayRequest(ChargeEntity charge, AppleDecryptedPaymentData applePaymentData) {
        super(charge);
        this.applePaymentData = applePaymentData;
    }

    public AppleDecryptedPaymentData getAppleDecryptedPaymentData() {
        return applePaymentData;
    }

    public static AuthorisationApplePayGatewayRequest valueOf(ChargeEntity charge, AppleDecryptedPaymentData applePaymentData) {
        return new AuthorisationApplePayGatewayRequest(charge, applePaymentData);
    }
}
