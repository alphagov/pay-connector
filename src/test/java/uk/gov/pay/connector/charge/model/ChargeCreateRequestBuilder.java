package uk.gov.pay.connector.charge.model;

import uk.gov.pay.commons.model.SupportedLanguage;

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

    public ChargeCreateRequest build() {
        return new ChargeCreateRequest(amount, description, reference, returnUrl, email, delayedCapture, language,
                prefilledCardHolderDetails, externalMetadata);
    }
}
