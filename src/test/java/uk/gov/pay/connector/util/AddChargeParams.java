package uk.gov.pay.connector.util;

import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.commons.model.charge.ExternalMetadata;
import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import static java.time.ZonedDateTime.now;

public class AddChargeParams {
    private final Long chargeId;
    private final String externalChargeId;
    private final String gatewayAccountId;
    private final long amount;
    private final ChargeStatus status;
    private final String returnUrl;
    private final String transactionId;
    private final String description;
    private final ServicePaymentReference reference;
    private final ZonedDateTime createdDate;
    private final long version;
    private final String email;
    private final String providerId;
    private final SupportedLanguage language;
    private final boolean delayedCapture;
    private final Long corporateSurcharge;
    private final ExternalMetadata externalMetadata;
    private final ParityCheckStatus parityCheckStatus;
    private final CardType cardType;

    private AddChargeParams(AddChargeParamsBuilder builder) {
        chargeId = builder.chargeId;
        externalChargeId = builder.externalChargeId;
        amount = builder.amount;
        gatewayAccountId = builder.gatewayAccountId;
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
        cardType = builder.cardType;
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

    public ZonedDateTime getCreatedDate() {
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
    
    public CardType getCardType() {
        return cardType;
    }

    public static final class AddChargeParamsBuilder {
        private Long chargeId = new Random().nextLong();
        private String externalChargeId = "anExternalChargeId";
        private String gatewayAccountId;
        private long amount = 1000;
        private ChargeStatus status = ChargeStatus.CAPTURED;
        private String returnUrl = "http://somereturn.gov.uk";
        private String transactionId;
        private String description = "Test description";
        private ServicePaymentReference reference = ServicePaymentReference.of("Test reference");
        private ZonedDateTime createdDate = now();
        private long version = 1;
        private String email = "test@example.com";
        private String providerId = "providerId";
        private SupportedLanguage language = SupportedLanguage.ENGLISH;
        private boolean delayedCapture = false;
        private Long corporateSurcharge;
        private ExternalMetadata externalMetadata;
        private ParityCheckStatus parityCheckStatus;
        private CardType cardType;

        private AddChargeParamsBuilder() {
        }

        public static AddChargeParamsBuilder anAddChargeParams() {
            return new AddChargeParamsBuilder();
        }

        public AddChargeParamsBuilder withChargeId(Long chargeId) {
            this.chargeId = chargeId;
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

        public AddChargeParamsBuilder withCreatedDate(ZonedDateTime createdDate) {
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
        
        public AddChargeParamsBuilder withCardType(CardType chargeType) {
            this.cardType = chargeType;
            return this;
        }

        public AddChargeParams build() {
            List.of(amount, status, returnUrl, gatewayAccountId, description, reference, externalChargeId)
                    .forEach(x -> Objects.requireNonNull(x));

            return new AddChargeParams(this);
        }
    }
}
