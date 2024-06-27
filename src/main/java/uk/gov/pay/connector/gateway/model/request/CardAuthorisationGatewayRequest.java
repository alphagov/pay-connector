package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.service.payments.commons.model.AuthorisationMode;
import uk.gov.service.payments.commons.model.SupportedLanguage;

import java.util.Optional;

public record CardAuthorisationGatewayRequest (
        String gatewayTransactionId,
        String email,
        SupportedLanguage language,
        boolean moto,
        String amount,
        String description,
        ServicePaymentReference reference,
        String govUkPayPaymentId,
        GatewayCredentials gatewayCredentials,
        GatewayAccountEntity gatewayAccount,
        AuthorisationMode authorisationMode,
        boolean savePaymentInstrumentToAgreement,
        Optional<AgreementEntity> agreement,
        AuthCardDetails authCardDetails
) implements AuthorisationGatewayRequest {
//    private AuthCardDetails authCardDetails;

//    public CardAuthorisationGatewayRequest(ChargeEntity charge, AuthCardDetails authCardDetails) {
//        super(charge);
//        this.authCardDetails = authCardDetails;
//    }

    public CardAuthorisationGatewayRequest(ChargeEntity charge, AuthCardDetails authCardDetails) {
        // NOTE: we don't store the ChargeEntity as we want to discourage code that deals with this request from
        // updating the charge in the database.
        
        this(
                charge.getGatewayTransactionId(),
                charge.getEmail(),
                charge.getLanguage(),
                charge.isMoto(),
                String.valueOf(CorporateCardSurchargeCalculator.getTotalAmountFor(charge)),
                charge.getDescription(),
                charge.getReference(),
                charge.getExternalId(),
                Optional.ofNullable(charge.getGatewayAccountCredentialsEntity())
                        .map(GatewayAccountCredentialsEntity::getCredentialsObject)
                        .orElse(null),
                charge.getGatewayAccount(),
                charge.getAuthorisationMode(),
                charge.isSavePaymentInstrumentToAgreement(),
                charge.getAgreement(),
                authCardDetails 
        );
//        this.gatewayTransactionId = charge.getGatewayTransactionId();
//        this.email = charge.getEmail();
//        this.language = charge.getLanguage();
//        this.moto = charge.isMoto();
//        this.amount = String.valueOf(CorporateCardSurchargeCalculator.getTotalAmountFor(charge));
//        this.description = charge.getDescription();
//        this.reference = charge.getReference();
//        this.govUkPayPaymentId = charge.getExternalId();
//        this.credentials = Optional.ofNullable(charge.getGatewayAccountCredentialsEntity())
//                .map(GatewayAccountCredentialsEntity::getCredentialsObject)
//                .orElse(null);
//        this.gatewayAccount = charge.getGatewayAccount();
//        this.authorisationMode = charge.getAuthorisationMode();
//        this.savePaymentInstrumentToAgreement = charge.isSavePaymentInstrumentToAgreement();
//        this.agreement = charge.getAgreement();
    }
    
    public CardAuthorisationGatewayRequest withNewTransactionId(String gatewayTransactionId) {
        return new CardAuthorisationGatewayRequest(this, this.authCardDetails, gatewayTransactionId);
    }
    
    private CardAuthorisationGatewayRequest(CardAuthorisationGatewayRequest other, AuthCardDetails authCardDetails, String gatewayTransactionId) {
        this(gatewayTransactionId,
                other.email(),
                other.language(),
                other.isMoto(),
                other.amount(),
                other.description(),
                other.reference(),
                other.govUkPayPaymentId(),
                other.gatewayCredentials(),
                other.gatewayAccount(),
                other.authorisationMode(),
                other.isSavePaymentInstrumentToAgreement(),
                other.agreement(),
                authCardDetails
        );
//        this.authCardDetails = authCardDetails;
    }

    public AuthCardDetails authCardDetails() {
        return authCardDetails;
    }

    @Override
    public boolean isMoto() {
        return moto;
    }

    @Override
    public Optional<String> getTransactionId() {
        return Optional.ofNullable(gatewayTransactionId);
    }

    @Override
    public boolean isSavePaymentInstrumentToAgreement() {
        return savePaymentInstrumentToAgreement;
    }

    @Override
    public GatewayOperation requestType() {
        return GatewayOperation.AUTHORISE;
    }
    
    @Override
    public boolean isForRecurringPayment() {
        return agreement.isPresent();
    }

    public static CardAuthorisationGatewayRequest valueOf(ChargeEntity charge, AuthCardDetails authCardDetails) {
        return new CardAuthorisationGatewayRequest(charge, authCardDetails);
    }
}
