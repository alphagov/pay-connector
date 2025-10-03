package uk.gov.pay.connector.events.eventdetails.charge;

import uk.gov.pay.connector.charge.model.AddressEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.common.model.domain.PaymentGatewayStateTransitions;
import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.service.payments.commons.model.AgreementPaymentType;
import uk.gov.service.payments.commons.model.AuthorisationMode;
import uk.gov.service.payments.commons.model.Source;
import uk.gov.service.payments.commons.model.charge.ExternalMetadata;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCELLED;

public class PaymentCreatedEventDetails extends EventDetails {

    private final Long amount;
    private final String description;
    private final String reference;
    private final String returnUrl;
    private final Long gatewayAccountId;
    private final String credentialExternalId;
    private final String paymentProvider;
    private final String language;
    private final boolean delayedCapture;
    private final boolean live;
    private final Map<String, Object> externalMetadata;
    private final String email;
    private final String cardholderName;
    private final String addressLine1;
    private final String addressLine2;
    private final String addressPostcode;
    private final String addressCity;
    private final String addressCounty;
    private final String addressCountry;
    private final Source source;
    private final boolean moto;
    private final String agreementId;
    private final AgreementPaymentType agreementPaymentType;
    private final String paymentInstrumentId;
    private final boolean savePaymentInstrumentToAgreement;
    private final AuthorisationMode authorisationMode;

    public PaymentCreatedEventDetails(Builder builder) {
        this.amount = builder.amount;
        this.description = builder.description;
        this.reference = builder.reference;
        this.returnUrl = builder.returnUrl;
        this.gatewayAccountId = builder.gatewayAccountId;
        this.credentialExternalId = builder.credentialExternalId;
        this.paymentProvider = builder.paymentProvider;
        this.language = builder.language;
        this.delayedCapture = builder.delayedCapture;
        this.live = builder.live;
        this.externalMetadata = builder.externalMetadata;
        this.email = builder.email;
        this.cardholderName = builder.cardholderName;
        this.addressLine1 = builder.addressLine1;
        this.addressLine2 = builder.addressLine2;
        this.addressPostcode = builder.addressPostcode;
        this.addressCity = builder.addressCity;
        this.addressCounty = builder.addressCounty;
        this.addressCountry = builder.addressCountry;
        this.source = builder.source;
        this.moto = builder.moto;
        this.agreementId = builder.agreementId;
        this.agreementPaymentType = builder.agreementPaymentType;
        this.paymentInstrumentId = builder.paymentInstrumentId;
        this.savePaymentInstrumentToAgreement = builder.savePaymentInstrumentToAgreement;
        this.authorisationMode = builder.authorisationMode;
    }

    public static PaymentCreatedEventDetails from(ChargeEntity charge) {
        var builder = new Builder()
                .withAmount(charge.getAmount())
                .withDescription(charge.getDescription())
                .withReference(charge.getReference().toString())
                .withReturnUrl(charge.getReturnUrl())
                .withGatewayAccountId(charge.getGatewayAccount().getId())
                .withPaymentProvider(charge.getPaymentProvider())
                .withLanguage(charge.getLanguage().toString())
                .withDelayedCapture(charge.isDelayedCapture())
                .withLive(charge.getGatewayAccount().isLive())
                .withExternalMetadata(charge.getExternalMetadata().map(ExternalMetadata::getMetadata).orElse(null))
                .withEmail(charge.getEmail())
                .withSource(charge.getSource())
                .withMoto(charge.isMoto())
                .withAgreementPaymentType(charge.getAgreementPaymentType())
                .withSavePaymentInstrumentToAgreement(charge.isSavePaymentInstrumentToAgreement())
                .withAuthorisationMode(charge.getAuthorisationMode());

        charge.getAgreement().ifPresent(agreementEntity -> builder.withAgreementId(agreementEntity.getExternalId()));
        charge.getPaymentInstrument().map(PaymentInstrumentEntity::getExternalId).ifPresent(builder::withPaymentInstrumentId);

        if (isAMotoAPIPayment(charge.getAuthorisationMode()) ||
                (isInCreatedState(charge) || hasNotGoneThroughAuthorisation(charge))) {
            addCardDetailsIfExist(charge, builder);
        }

        if (charge.getGatewayAccountCredentialsEntity() != null) {
            builder.withCredentialExternalId(charge.getGatewayAccountCredentialsEntity().getExternalId());
        }

        return builder.build();
    }

    private static boolean isInCreatedState(ChargeEntity charge) {
        return ChargeStatus.CREATED.equals(ChargeStatus.fromString(charge.getStatus()));
    }

    private static boolean isAMotoAPIPayment(AuthorisationMode chargeAuthorisationMode) {
        return AuthorisationMode.MOTO_API == chargeAuthorisationMode;
    }

    private static boolean hasNotGoneThroughAuthorisation(ChargeEntity charge) {
        return charge.getEvents().stream()
                .map(ChargeEventEntity::getStatus)
                .noneMatch(PaymentCreatedEventDetails::containsPostAuthorisationReadyStatus);
    }

    private static boolean containsPostAuthorisationReadyStatus(ChargeStatus status) {
        return PaymentGatewayStateTransitions.getInstance()
                .getNextStatus(AUTHORISATION_READY)
                .stream().filter(chargeStatus -> !(chargeStatus.equals(EXPIRED) || chargeStatus.equals(USER_CANCELLED)))
                .collect(Collectors.toList())
                .contains(status);
    }

    private static void addCardDetailsIfExist(ChargeEntity charge, Builder builder) {
        Optional.ofNullable(charge.getCardDetails()).ifPresent(
                cardDetails ->
                        builder.withCardholderName(cardDetails.getCardHolderName())
                                .withAddressLine1(cardDetails.getBillingAddress().map(AddressEntity::getLine1).orElse(null))
                                .withAddressLine2(cardDetails.getBillingAddress().map(AddressEntity::getLine2).orElse(null))
                                .withAddressCity(cardDetails.getBillingAddress().map(AddressEntity::getCity).orElse(null))
                                .withAddressCountry(cardDetails.getBillingAddress().map(AddressEntity::getCountry).orElse(null))
                                .withAddressCounty(cardDetails.getBillingAddress().map(AddressEntity::getCounty).orElse(null))
                                .withAddressPostcode(cardDetails.getBillingAddress().map(AddressEntity::getPostcode).orElse(null)));
    }

    public Long getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public String getReference() {
        return reference;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public String getGatewayAccountId() {
        return gatewayAccountId.toString();
    }

    public String getCredentialExternalId() {
        return credentialExternalId;
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public String getLanguage() {
        return language;
    }

    public boolean isDelayedCapture() {
        return delayedCapture;
    }

    public boolean isLive() {
        return live;
    }

    public boolean isMoto() {
        return moto;
    }

    public Map<String, Object> getExternalMetadata() {
        return externalMetadata;
    }

    public String getEmail() {
        return email;
    }

    public String getCardholderName() {
        return cardholderName;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getAddressPostcode() {
        return addressPostcode;
    }

    public String getAddressCity() {
        return addressCity;
    }

    public String getAddressCounty() {
        return addressCounty;
    }

    public String getAddressCountry() {
        return addressCountry;
    }

    public Source getSource() {
        return source;
    }

    public String getAgreementId() {
        return agreementId;
    }

    public String getPaymentInstrumentId() {
        return paymentInstrumentId;
    }

    public boolean isSavePaymentInstrumentToAgreement() {
        return savePaymentInstrumentToAgreement;
    }

    public AgreementPaymentType getAgreementPaymentType() {
        return agreementPaymentType;
    }

    public AuthorisationMode getAuthorisationMode() {
        return authorisationMode;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentCreatedEventDetails that = (PaymentCreatedEventDetails) o;
        return Objects.equals(amount, that.amount) &&
                Objects.equals(description, that.description) &&
                Objects.equals(reference, that.reference) &&
                Objects.equals(returnUrl, that.returnUrl) &&
                Objects.equals(gatewayAccountId, that.gatewayAccountId) &&
                Objects.equals(credentialExternalId, that.credentialExternalId) &&
                Objects.equals(paymentProvider, that.paymentProvider) &&
                Objects.equals(language, that.language) &&
                Objects.equals(delayedCapture, that.delayedCapture) &&
                Objects.equals(live, that.live) &&
                Objects.equals(externalMetadata, that.externalMetadata) &&
                Objects.equals(email, that.email) &&
                Objects.equals(cardholderName, that.cardholderName) &&
                Objects.equals(addressLine1, that.addressLine1) &&
                Objects.equals(addressLine2, that.addressLine2) &&
                Objects.equals(addressPostcode, that.addressPostcode) &&
                Objects.equals(addressCity, that.addressCity) &&
                Objects.equals(addressCounty, that.addressCounty) &&
                Objects.equals(addressCountry, that.addressCountry) &&
                Objects.equals(agreementId, that.agreementId) &&
                Objects.equals(agreementPaymentType, that.agreementPaymentType) &&
                Objects.equals(paymentInstrumentId, that.paymentInstrumentId) &&
                Objects.equals(savePaymentInstrumentToAgreement, that.savePaymentInstrumentToAgreement) &&
                Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, description, reference, returnUrl, gatewayAccountId, credentialExternalId,
                paymentProvider, language, delayedCapture, live, externalMetadata, agreementId, agreementPaymentType,
                paymentInstrumentId, savePaymentInstrumentToAgreement, source);
    }

    public static class Builder {
        private Long amount;
        private String description;
        private String reference;
        private String returnUrl;
        private Long gatewayAccountId;
        private String paymentProvider;
        private String language;
        private boolean delayedCapture;
        private boolean live;
        private Map<String, Object> externalMetadata;
        private String email;
        private String cardholderName;
        private String addressLine1;
        private String addressLine2;
        private String addressPostcode;
        private String addressCity;
        private String addressCounty;
        private String addressCountry;
        private Source source;
        private boolean moto;
        private String credentialExternalId;
        private String agreementId;
        private AgreementPaymentType agreementPaymentType;
        private String paymentInstrumentId;
        private boolean savePaymentInstrumentToAgreement;
        private AuthorisationMode authorisationMode;

        public PaymentCreatedEventDetails build() {
            return new PaymentCreatedEventDetails(this);
        }

        public Builder withAmount(Long amount) {
            this.amount = amount;
            return this;
        }

        public Builder withDescription(String description) {
            this.description = description;
            return this;
        }

        public Builder withReference(String reference) {
            this.reference = reference;
            return this;
        }

        public Builder withReturnUrl(String returnUrl) {
            this.returnUrl = returnUrl;
            return this;
        }

        public Builder withGatewayAccountId(Long gatewayAccountId) {
            this.gatewayAccountId = gatewayAccountId;
            return this;
        }

        public Builder withPaymentProvider(String paymentProvider) {
            this.paymentProvider = paymentProvider;
            return this;
        }

        public Builder withLanguage(String language) {
            this.language = language;
            return this;
        }

        public Builder withDelayedCapture(boolean delayedCapture) {
            this.delayedCapture = delayedCapture;
            return this;
        }

        public Builder withLive(boolean live) {
            this.live = live;
            return this;
        }

        public Builder withExternalMetadata(Map<String, Object> externalMetadata) {
            this.externalMetadata = externalMetadata;
            return this;
        }

        public Builder withEmail(String email) {
            this.email = email;
            return this;
        }

        public Builder withCardholderName(String cardholderName) {
            this.cardholderName = cardholderName;
            return this;
        }

        public Builder withAddressLine1(String addressLine1) {
            this.addressLine1 = addressLine1;
            return this;
        }

        public Builder withAddressLine2(String addressLine2) {
            this.addressLine2 = addressLine2;
            return this;
        }

        public Builder withAddressPostcode(String addressPostcode) {
            this.addressPostcode = addressPostcode;
            return this;
        }

        public Builder withAddressCity(String addressCity) {
            this.addressCity = addressCity;
            return this;
        }

        public Builder withAddressCounty(String addressCounty) {
            this.addressCounty = addressCounty;
            return this;
        }

        public Builder withAddressCountry(String addressCountry) {
            this.addressCountry = addressCountry;
            return this;
        }

        public Builder withSource(Source source) {
            this.source = source;
            return this;
        }

        public Builder withMoto(boolean moto) {
            this.moto = moto;
            return this;
        }

        public Builder withAgreementId(String agreementId) {
            this.agreementId = agreementId;
            return this;
        }

        public Builder withAgreementPaymentType(AgreementPaymentType agreementPaymentType) {
            this.agreementPaymentType = agreementPaymentType;
            return this;
        }

        public Builder withPaymentInstrumentId(String paymentInstrumentId) {
            this.paymentInstrumentId = paymentInstrumentId;
            return this;
        }

        public Builder withSavePaymentInstrumentToAgreement(boolean savePaymentInstrumentToAgreement) {
            this.savePaymentInstrumentToAgreement = savePaymentInstrumentToAgreement;
            return this;
        }

        public Builder withCredentialExternalId(String credentialExternalId) {
            this.credentialExternalId = credentialExternalId;
            return this;
        }

        public Builder withAuthorisationMode(AuthorisationMode authorisationMode) {
            this.authorisationMode = authorisationMode;
            return this;
        }

    }
}
