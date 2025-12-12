package uk.gov.pay.connector.util;

import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.Exemption3dsType;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.pay.connector.paymentprocessor.model.Exemption3ds;
import uk.gov.service.payments.commons.model.AgreementPaymentType;
import uk.gov.service.payments.commons.model.AuthorisationMode;
import uk.gov.service.payments.commons.model.SupportedLanguage;
import uk.gov.service.payments.commons.model.charge.ExternalMetadata;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import static uk.gov.pay.connector.util.RandomGeneratorUtils.randomLong;

public record AddChargeParams(
        Long chargeId,
        String externalChargeId,
        String gatewayAccountId,
        String paymentProvider,
        long amount,
        ChargeStatus status,
        String returnUrl,
        String transactionId,
        String description,
        ServicePaymentReference reference,
        Instant createdDate,
        long version,
        String email,
        String providerId,
        SupportedLanguage language,
        boolean delayedCapture,
        Long corporateSurcharge,
        ExternalMetadata externalMetadata,
        ParityCheckStatus parityCheckStatus,
        CardType cardType,
        ZonedDateTime parityCheckDate,
        Long gatewayCredentialId,
        String serviceId,
        String issuerUrl,
        String agreementExternalId,
        boolean savePaymentInstrumentToAgreement,
        AuthorisationMode authorisationMode,
        Instant updatedDate,
        Long paymentInstrumentId,
        Boolean canRetry,
        Boolean requires3ds,
        Exemption3ds exemption3ds,
        Exemption3dsType exemption3dsType,
        AgreementPaymentType agreementPaymentType) {

    public static final class AddChargeParamsBuilder {

        public AddChargeParams build() {
            List.of(amount, status, returnUrl, gatewayAccountId, description, reference, externalChargeId)
                    .forEach(Objects::requireNonNull);

            return new AddChargeParams(chargeId, externalChargeId, gatewayAccountId, paymentProvider, amount, status,
                    returnUrl, transactionId, description, reference, createdDate, version, email, providerId, language,
                    delayedCapture, corporateSurcharge, externalMetadata, parityCheckStatus, cardType, parityCheckDate,
                    gatewayCredentialId, serviceId, issuerUrl, agreementExternalId, savePaymentInstrumentToAgreement,
                    authorisationMode, updatedDate, paymentInstrumentId, canRetry, requires3ds, exemption3ds, exemption3dsType,
                    agreementPaymentType);
        }

        private String agreementExternalId;
        private long amount = 1000;
        private AuthorisationMode authorisationMode = AuthorisationMode.WEB;
        private Boolean canRetry;
        private CardType cardType;
        private Long chargeId = randomLong();
        private Long corporateSurcharge;
        private Instant createdDate = Instant.now();
        private boolean delayedCapture = false;
        private String description = "Test description";
        private String email = "test@example.com";
        private Exemption3ds exemption3ds;
        private Exemption3dsType exemption3dsType;
        private String externalChargeId = "anExternalChargeId";
        private ExternalMetadata externalMetadata;
        private String gatewayAccountId;
        private Long gatewayCredentialId;
        private String issuerUrl;
        private SupportedLanguage language = SupportedLanguage.ENGLISH;
        private ZonedDateTime parityCheckDate;
        private ParityCheckStatus parityCheckStatus;
        private String paymentProvider = "sandbox";
        private Long paymentInstrumentId;
        private String providerId = "providerId";
        private ServicePaymentReference reference = ServicePaymentReference.of("Test reference");
        private Boolean requires3ds;
        private String returnUrl = "http://somereturn.gov.uk";
        private boolean savePaymentInstrumentToAgreement;
        private String serviceId;
        private ChargeStatus status = ChargeStatus.CAPTURED;
        private String transactionId;
        private Instant updatedDate;
        private long version = 1;
        private AgreementPaymentType agreementPaymentType;

        private AddChargeParamsBuilder() {
        }

        public static AddChargeParamsBuilder anAddChargeParams() {
            return new AddChargeParamsBuilder();
        }

        public AddChargeParamsBuilder withChargeId(Long chargeId) {
            this.chargeId = chargeId;
            return this;
        }

        public AddChargeParamsBuilder withServiceId(String serviceId) {
            this.serviceId = serviceId;
            return this;
        }

        public AddChargeParamsBuilder withExternalChargeId(String externalChargeId) {
            this.externalChargeId = externalChargeId;
            return this;
        }

        public AddChargeParamsBuilder withGatewayAccountId(String gatewayAccountId) {
            this.gatewayAccountId = gatewayAccountId;
            return this;
        }

        public AddChargeParamsBuilder withPaymentProvider(String paymentProvider) {
            this.paymentProvider = paymentProvider;
            return this;
        }

        public AddChargeParamsBuilder withAmount(long amount) {
            this.amount = amount;
            return this;
        }

        public AddChargeParamsBuilder withStatus(ChargeStatus status) {
            this.status = status;
            return this;
        }

        public AddChargeParamsBuilder withReturnUrl(String returnUrl) {
            this.returnUrl = returnUrl;
            return this;
        }

        public AddChargeParamsBuilder withTransactionId(String transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        public AddChargeParamsBuilder withDescription(String description) {
            this.description = description;
            return this;
        }

        public AddChargeParamsBuilder withReference(ServicePaymentReference reference) {
            this.reference = reference;
            return this;
        }

        public AddChargeParamsBuilder withCreatedDate(Instant createdDate) {
            this.createdDate = createdDate;
            return this;
        }

        public AddChargeParamsBuilder withVersion(long version) {
            this.version = version;
            return this;
        }

        public AddChargeParamsBuilder withEmail(String email) {
            this.email = email;
            return this;
        }

        public AddChargeParamsBuilder withProviderId(String providerId) {
            this.providerId = providerId;
            return this;
        }

        public AddChargeParamsBuilder withLanguage(SupportedLanguage language) {
            this.language = language;
            return this;
        }

        public AddChargeParamsBuilder withDelayedCapture(boolean delayedCapture) {
            this.delayedCapture = delayedCapture;
            return this;
        }

        public AddChargeParamsBuilder withCorporateSurcharge(Long corporateSurcharge) {
            this.corporateSurcharge = corporateSurcharge;
            return this;
        }

        public AddChargeParamsBuilder withExternalMetadata(ExternalMetadata externalMetadata) {
            this.externalMetadata = externalMetadata;
            return this;
        }

        public AddChargeParamsBuilder withParityCheckStatus(ParityCheckStatus parityCheckStatus) {
            this.parityCheckStatus = parityCheckStatus;
            return this;
        }

        public AddChargeParamsBuilder withParityCheckDate(ZonedDateTime parityCheckDate) {
            this.parityCheckDate = parityCheckDate;
            return this;
        }

        public AddChargeParamsBuilder withCardType(CardType chargeType) {
            this.cardType = chargeType;
            return this;
        }

        public AddChargeParamsBuilder withGatewayCredentialId(Long gatewayCredentialId) {
            this.gatewayCredentialId = gatewayCredentialId;
            return this;
        }

        public AddChargeParamsBuilder withIssuerUrl(String issuerUrl) {
            this.issuerUrl = issuerUrl;
            return this;
        }

        public AddChargeParamsBuilder withAgreementExternalId(String agreementExternalId) {
            this.agreementExternalId = agreementExternalId;
            return this;
        }

        public AddChargeParamsBuilder withSavePaymentInstrumentToAgreement(boolean savePaymentInstrumentToAgreement) {
            this.savePaymentInstrumentToAgreement = savePaymentInstrumentToAgreement;
            return this;
        }

        public AddChargeParamsBuilder withAgreementPaymentType(AgreementPaymentType agreementPaymentType) {
            this.agreementPaymentType = agreementPaymentType;
            return this;
        }

        public AddChargeParamsBuilder withAuthorisationMode(AuthorisationMode authorisationMode) {
            this.authorisationMode = authorisationMode;
            return this;
        }

        public AddChargeParamsBuilder withUpdatedDate(Instant updatedDate) {
            this.updatedDate = updatedDate;
            return this;
        }

        public AddChargeParamsBuilder withPaymentInstrumentId(Long paymentInstrumentId) {
            this.paymentInstrumentId = paymentInstrumentId;
            return this;
        }

        public AddChargeParamsBuilder withCanRetry(Boolean canRetry) {
            this.canRetry = canRetry;
            return this;
        }

        public AddChargeParamsBuilder withRequires3ds(Boolean requires3ds) {
            this.requires3ds = requires3ds;
            return this;
        }

        public AddChargeParamsBuilder withExemption3ds(Exemption3ds exemption3ds) {
            this.exemption3ds = exemption3ds;
            return this;
        }

        public AddChargeParamsBuilder withExemption3dsType(Exemption3dsType exemption3dsType) {
            this.exemption3dsType = exemption3dsType;
            return this;
        }
    }
}
