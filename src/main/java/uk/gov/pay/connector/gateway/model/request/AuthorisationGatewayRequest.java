package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.service.payments.commons.model.AuthorisationMode;
import uk.gov.service.payments.commons.model.SupportedLanguage;

import java.util.Map;
import java.util.Optional;

public abstract class AuthorisationGatewayRequest implements GatewayRequest {
    private final String gatewayTransactionId;
    private final String email;
    private final SupportedLanguage language;
    private final boolean moto;
    private final String amount;
    private final String description;
    private final ServicePaymentReference reference;
    private final String govUkPayPaymentId;
    private final Map<String, Object> credentials;
    private final GatewayAccountEntity gatewayAccount;
    private final AuthorisationMode authorisationMode;
    private final boolean savePaymentInstrumentToAgreement;
    private final Optional<AgreementEntity> agreement;
    
    protected AuthorisationGatewayRequest(ChargeEntity charge) {
        // NOTE: we don't store the ChargeEntity as we want to discourage code that deals with this request from
        // updating the charge in the database.
        this.gatewayTransactionId = charge.getGatewayTransactionId();
        this.email = charge.getEmail();
        this.language = charge.getLanguage();
        this.moto = charge.isMoto();
        this.amount = String.valueOf(CorporateCardSurchargeCalculator.getTotalAmountFor(charge));
        this.description = charge.getDescription();
        this.reference = charge.getReference();
        this.govUkPayPaymentId = charge.getExternalId();
        this.credentials = Optional.ofNullable(charge.getGatewayAccountCredentialsEntity()).map(GatewayAccountCredentialsEntity::getCredentials).orElse(null);
        this.gatewayAccount = charge.getGatewayAccount();
        this.authorisationMode = charge.getAuthorisationMode();
        this.savePaymentInstrumentToAgreement = charge.isSavePaymentInstrumentToAgreement();
        this.agreement = charge.getAgreement();
    }

    public AuthorisationGatewayRequest(String gatewayTransactionId,
                                       String email,
                                       SupportedLanguage language,
                                       boolean moto,
                                       String amount,
                                       String description,
                                       ServicePaymentReference reference,
                                       String govUkPayPaymentId,
                                       Map<String, Object> credentials,
                                       GatewayAccountEntity gatewayAccount,
                                       AuthorisationMode authorisationMode,
                                       boolean savePaymentInstrumentToAgreement,
                                       AgreementEntity agreement) {
        this.gatewayTransactionId = gatewayTransactionId;
        this.email = email;
        this.language = language;
        this.moto = moto;
        this.amount = amount;
        this.description = description;
        this.reference = reference;
        this.govUkPayPaymentId = govUkPayPaymentId;
        this.credentials = credentials;
        this.gatewayAccount = gatewayAccount;
        this.authorisationMode = authorisationMode;
        this.savePaymentInstrumentToAgreement = savePaymentInstrumentToAgreement;
        this.agreement = Optional.ofNullable(agreement);
    }

    public String getEmail() {
        return email;
    }

    public boolean isMoto() {
        return moto;
    }

    public SupportedLanguage getLanguage() {
        return language;
    }

    public Optional<String> getTransactionId() {
        return Optional.ofNullable(gatewayTransactionId);
    }

    public String getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public ServicePaymentReference getReference() {
        return reference;
    }

    public String getGovUkPayPaymentId() {
        return govUkPayPaymentId;
    }

    @Override
    public Map<String, Object> getGatewayCredentials() {
        return credentials;
    }

    @Override
    public GatewayAccountEntity getGatewayAccount() {
        return gatewayAccount;
    }

    @Override
    public GatewayOperation getRequestType() {
        return GatewayOperation.AUTHORISE;
    }

    @Override
    public AuthorisationMode getAuthorisationMode() {
        return authorisationMode;
    }

    public boolean isSavePaymentInstrumentToAgreement() {
        return savePaymentInstrumentToAgreement;
    }

    public Optional<AgreementEntity> getAgreement() {
        return agreement;
    }

    @Override
    public boolean isForRecurringPayment() {
        return agreement.isPresent();
    }
}
