package uk.gov.pay.connector.applepay;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;

public class ApplePayAuthorisationGatewayRequest extends AuthorisationGatewayRequest {
    private AppleDecryptedPaymentData applePaymentData;

    public ApplePayAuthorisationGatewayRequest(ChargeEntity charge, AppleDecryptedPaymentData applePaymentData) {
        super(charge);
        this.applePaymentData = applePaymentData;
    }

    public AppleDecryptedPaymentData getAppleDecryptedPaymentData() {
        return applePaymentData;
    }

    public static ApplePayAuthorisationGatewayRequest valueOf(ChargeEntity charge, AppleDecryptedPaymentData applePaymentData) {
        return new ApplePayAuthorisationGatewayRequest(charge, applePaymentData);
    }
}
