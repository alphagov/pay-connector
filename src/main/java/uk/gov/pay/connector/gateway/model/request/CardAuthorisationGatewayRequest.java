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
        Optional<String> transactionId,
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
    public CardAuthorisationGatewayRequest(ChargeEntity charge, AuthCardDetails authCardDetails) {
        // NOTE: we don't store the ChargeEntity as we want to discourage code that deals with this request from
        // updating the charge in the database.
        
        this(
                Optional.ofNullable(charge.getGatewayTransactionId()),
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
    }
    
    public CardAuthorisationGatewayRequest withNewTransactionId(String gatewayTransactionId) {
        return new CardAuthorisationGatewayRequest(this, this.authCardDetails, gatewayTransactionId);
    }
    
    private CardAuthorisationGatewayRequest(CardAuthorisationGatewayRequest other, AuthCardDetails authCardDetails, String gatewayTransactionId) {
        this(
                Optional.ofNullable(gatewayTransactionId),
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
    }

//    public AuthCardDetails authCardDetails() {
//        return authCardDetails;
//    }

    @Override
    public boolean isMoto() {
        return moto;
    }

//    @Override
//    public Optional<String> getTransactionId() {
//        return Optional.ofNullable(gatewayTransactionId);
//    }

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
