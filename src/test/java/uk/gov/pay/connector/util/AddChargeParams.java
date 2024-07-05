package uk.gov.pay.connector.util;

import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.service.payments.commons.model.AuthorisationMode;
import uk.gov.service.payments.commons.model.SupportedLanguage;
import uk.gov.service.payments.commons.model.charge.ExternalMetadata;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class AddChargeParams {
    private final Long chargeId;
    private final String externalChargeId;
    private final String gatewayAccountId;
    private final String paymentProvider;
    private final long amount;
    private final ChargeStatus status;
    private final String returnUrl;
    private final String transactionId;
    private final String description;
    private final ServicePaymentReference reference;
    private final Instant createdDate;
    private final long version;
    private final String email;
    private final String providerId;
    private final SupportedLanguage language;
    private final boolean delayedCapture;
    private final Long corporateSurcharge;
    private final ExternalMetadata externalMetadata;
    private final ParityCheckStatus parityCheckStatus;
    private final CardType cardType;
    private final ZonedDateTime parityCheckDate;
    private final Long gatewayCredentialId;
    private final String serviceId;
    private final String issuerUrl;
    private final String agreementExternalId;
    private final boolean savePaymentInstrumentToAgreement;
    private final AuthorisationMode authorisationMode;
    private final Instant updatedDate;
    private final Long paymentInstrumentId;
    private final Boolean canRetry;

    private AddChargeParams(AddChargeParamsBuilder builder) {
        chargeId = builder.chargeId;
        externalChargeId = builder.externalChargeId;
        amount = builder.amount;
        gatewayAccountId = builder.gatewayAccountId;
        paymentProvider = builder.paymentProvider;
        status = builder.status;
        returnUrl = builder.returnUrl;
        transactionId = builder.transactionId;
        description = builder.description;
        reference = builder.reference;
        createdDate = builder.createdDate;
        version = builder.version;
        email = builder.email;
        providerId = builder.providerId;
        language = builder.language;
        delayedCapture = builder.delayedCapture;
        corporateSurcharge = builder.corporateSurcharge;
        externalMetadata = builder.externalMetadata;
        parityCheckStatus = builder.parityCheckStatus;
        parityCheckDate = builder.parityCheckDate;
        cardType = builder.cardType;
        gatewayCredentialId = builder.gatewayCredentialId;
        serviceId = builder.serviceId;
        issuerUrl = builder.issuerUrl;
        agreementExternalId = builder.agreementExternalId;
        savePaymentInstrumentToAgreement = builder.savePaymentInstrumentToAgreement;
        authorisationMode = builder.authorisationMode;
        this.updatedDate = builder.updatedDate;
        paymentInstrumentId = builder.paymentInstrumentId;
        canRetry = builder.canRetry;
    }

    public Long getChargeId() {
        return chargeId;
    }

    public String getExternalChargeId() {
        return externalChargeId;
    }

    public String getGatewayAccountId() {
        return gatewayAccountId;
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public long getAmount() {
        return amount;
    }

    public ChargeStatus getStatus() {
        return status;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getDescription() {
        return description;
    }

    public ServicePaymentReference getReference() {
        return reference;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public long getVersion() {
        return version;
    }

    public String getEmail() {
        return email;
    }

    public String getProviderId() {
        return providerId;
    }

    public SupportedLanguage getLanguage() {
        return language;
    }

    public boolean isDelayedCapture() {
        return delayedCapture;
    }

    public Long getCorporateSurcharge() {
        return corporateSurcharge;
    }

    public ExternalMetadata getExternalMetadata() {
        return externalMetadata;
    }

    public ParityCheckStatus getParityCheckStatus() {
        return parityCheckStatus;
    }

    public ZonedDateTime getParityCheckDate() {
        return parityCheckDate;
    }
    
    public CardType getCardType() {
        return cardType;
    }

    public Long getGatewayCredentialId() {
        return gatewayCredentialId;
    }

    public String getServiceId() {
        return serviceId;
    }

    public String getIssuerUrl() {
        return issuerUrl;
    }

    public boolean getSavePaymentInstrumentToAgreement() {
        return savePaymentInstrumentToAgreement;
    }

    public String getAgreementExternalId() {
        return agreementExternalId;
    }

    public AuthorisationMode getAuthorisationMode() {
        return authorisationMode;
    }

    public Instant getUpdatedDate() {
        return updatedDate;
    }

    public Long getPaymentInstrumentId() {
        return paymentInstrumentId;
    }

    public Boolean getCanRetry() {
        return canRetry;
    }

    public static final class AddChargeParamsBuilder {
        private Long chargeId = new Random().nextLong();
        private String externalChargeId = "anExternalChargeId";
        private String gatewayAccountId;
        private String paymentProvider = "sandbox";
        private long amount = 1000;
        private ChargeStatus status = ChargeStatus.CAPTURED;
        private String returnUrl = "http://somereturn.gov.uk";
        private String transactionId;
        private String description = "Test description";
        private ServicePaymentReference reference = ServicePaymentReference.of("Test reference");
        private Instant createdDate = Instant.now();
        private long version = 1;
        private String email = "test@example.com";
        private String providerId = "providerId";
        private SupportedLanguage language = SupportedLanguage.ENGLISH;
        private boolean delayedCapture = false;
        private Long corporateSurcharge;
        private ExternalMetadata externalMetadata;
        private ParityCheckStatus parityCheckStatus;
        private CardType cardType;
        private ZonedDateTime parityCheckDate;
        private Long gatewayCredentialId;
        private String serviceId;
        private String issuerUrl;
        private String agreementExternalId;
        private boolean savePaymentInstrumentToAgreement;
        private AuthorisationMode authorisationMode = AuthorisationMode.WEB;
        private Instant updatedDate;
        private Long paymentInstrumentId;
        private Boolean canRetry;

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

        public AddChargeParams build() {
            List.of(amount, status, returnUrl, gatewayAccountId, description, reference, externalChargeId)
                    .forEach(Objects::requireNonNull);

            return new AddChargeParams(this);
        }
    }
}
