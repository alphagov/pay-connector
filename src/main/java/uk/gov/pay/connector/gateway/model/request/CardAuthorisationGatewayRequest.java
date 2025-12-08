package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;

public class CardAuthorisationGatewayRequest extends AuthorisationGatewayRequest {
    private AuthCardDetails authCardDetails;

    public CardAuthorisationGatewayRequest(ChargeEntity charge, AuthCardDetails authCardDetails) {
        super(charge);
        this.authCardDetails = authCardDetails;
    }

    public CardAuthorisationGatewayRequest withNewTransactionId(String gatewayTransactionId) {
        return new CardAuthorisationGatewayRequest(this, this.authCardDetails, gatewayTransactionId);
    }

    private CardAuthorisationGatewayRequest(CardAuthorisationGatewayRequest other, AuthCardDetails authCardDetails, String gatewayTransactionId) {
        super(gatewayTransactionId,
                other.getEmail(),
                other.getLanguage(),
                other.isMoto(),
                other.getAmount(),
                other.getDescription(),
                other.getReference(),
                other.getGovUkPayPaymentId(),
                other.getGatewayCredentials(),
                other.getGatewayAccount(),
                other.getAuthorisationMode(),
                other.isSavePaymentInstrumentToAgreement(),
                other.getAgreementPaymentType(),
                other.getAgreement().orElse(null));
        this.authCardDetails = authCardDetails;
    }

    public AuthCardDetails getAuthCardDetails() {
        return authCardDetails;
    }

    public static CardAuthorisationGatewayRequest valueOf(ChargeEntity charge, AuthCardDetails authCardDetails) {
        return new CardAuthorisationGatewayRequest(charge, authCardDetails);
    }
}
