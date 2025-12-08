package uk.gov.pay.connector.charge.model;

import uk.gov.service.payments.commons.model.AgreementPaymentType;
import uk.gov.service.payments.commons.model.AuthorisationMode;
import uk.gov.service.payments.commons.model.Source;
import uk.gov.service.payments.commons.model.SupportedLanguage;
import uk.gov.service.payments.commons.model.charge.ExternalMetadata;

public final class ChargeCreateRequestBuilder {
    private long amount;
    private String description;
    private String reference;
    private String returnUrl;
    private String email;
    private boolean delayedCapture;
    private SupportedLanguage language;
    private PrefilledCardHolderDetails prefilledCardHolderDetails;
    private ExternalMetadata externalMetadata;
    private Source source;
    private boolean moto;
    private String agreementId;
    private AgreementPaymentType agreementPaymentType;
    private boolean savePaymentInstrumentToAgreement;
    private AuthorisationMode authorisationMode;
    private String credentialExternalId;

    private ChargeCreateRequestBuilder() {
    }

    public static ChargeCreateRequestBuilder aChargeCreateRequest() {
        return new ChargeCreateRequestBuilder();
    }

    public ChargeCreateRequestBuilder withAmount(long amount) {
        this.amount = amount;
        return this;
    }

    public ChargeCreateRequestBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    public ChargeCreateRequestBuilder withReference(String reference) {
        this.reference = reference;
        return this;
    }

    public ChargeCreateRequestBuilder withReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
        return this;
    }

    public ChargeCreateRequestBuilder withEmail(String email) {
        this.email = email;
        return this;
    }

    public ChargeCreateRequestBuilder withDelayedCapture(boolean delayedCapture) {
        this.delayedCapture = delayedCapture;
        return this;
    }

    public ChargeCreateRequestBuilder withLanguage(SupportedLanguage language) {
        this.language = language;
        return this;
    }

    public ChargeCreateRequestBuilder withPrefilledCardHolderDetails(PrefilledCardHolderDetails prefilledCardHolderDetails) {
        this.prefilledCardHolderDetails = prefilledCardHolderDetails;
        return this;
    }

    public ChargeCreateRequestBuilder withExternalMetadata(ExternalMetadata externalMetadata) {
        this.externalMetadata = externalMetadata;
        return this;
    }

    public ChargeCreateRequestBuilder withSource(Source source) {
        this.source = source;
        return this;
    }

    public ChargeCreateRequestBuilder withMoto(boolean moto) {
        this.moto = moto;
        return this;
    }

    public ChargeCreateRequestBuilder withAgreementId(String agreementId) {
        this.agreementId = agreementId;
        return this;
    }

    public ChargeCreateRequestBuilder withAgreementPaymentType(AgreementPaymentType agreementPaymentType) {
        this.agreementPaymentType = agreementPaymentType;
        return this;
    }

    public ChargeCreateRequestBuilder withSavePaymentInstrumentToAgreement(boolean savePaymentInstrumentToAgreement) {
        this.savePaymentInstrumentToAgreement = savePaymentInstrumentToAgreement;
        return this;
    }

    public ChargeCreateRequestBuilder withAuthorisationMode(AuthorisationMode authorisationMode) {
        this.authorisationMode = authorisationMode;
        return this;
    }

    public ChargeCreateRequestBuilder withCredentialId(String credentialExternalId) {
        this.credentialExternalId = credentialExternalId;
        return this;
    }

    public ChargeCreateRequest build() {
        return new ChargeCreateRequest(amount, description, reference, returnUrl, email, delayedCapture, language,
                prefilledCardHolderDetails, externalMetadata, source, moto, agreementId, agreementPaymentType,
                savePaymentInstrumentToAgreement, authorisationMode, credentialExternalId);
    }
}
