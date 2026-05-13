package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.service.payments.commons.model.AgreementPaymentType;
import uk.gov.service.payments.commons.model.AuthorisationMode;
import uk.gov.service.payments.commons.model.SupportedLanguage;

public class CardAuthorisationGatewayRequest extends AuthorisationGatewayRequest {
    private final AuthCardDetails authCardDetails;

    private CardAuthorisationGatewayRequest(ChargeEntity charge, AuthCardDetails authCardDetails) {
        super(charge);
        this.authCardDetails = authCardDetails;
    }

     CardAuthorisationGatewayRequest(String gatewayTransactionId,
                                     String email,
                                     SupportedLanguage language,
                                     boolean moto,
                                     String amount,
                                     String description,
                                     ServicePaymentReference reference,
                                     String govUkPayPaymentId,
                                     GatewayCredentials credentials,
                                     GatewayAccountEntity gatewayAccount,
                                     AuthorisationMode authorisationMode,
                                     boolean savePaymentInstrumentToAgreement,
                                     AgreementPaymentType agreementPaymentType,
                                     AgreementEntity agreement,
                                     AuthCardDetails authCardDetails) {
        super(gatewayTransactionId, email, language, moto, amount, description, reference, govUkPayPaymentId, credentials, gatewayAccount, authorisationMode, savePaymentInstrumentToAgreement, agreementPaymentType, agreement);
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
