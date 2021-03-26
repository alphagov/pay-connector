package uk.gov.pay.connector.charge.model.telephone;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import uk.gov.service.payments.commons.model.CardExpiryDate;
import uk.gov.pay.connector.charge.validation.telephone.ValidCardBrand;
import uk.gov.pay.connector.charge.validation.telephone.ValidCardFirstSixDigits;
import uk.gov.pay.connector.charge.validation.telephone.ValidCardLastFourDigits;
import uk.gov.pay.connector.charge.validation.telephone.ValidZonedDateTime;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Optional;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class TelephoneChargeCreateRequest {
    
    @NotNull(message = "Field [amount] cannot be null")
    private Long amount;
    
    @NotNull(message = "Field [reference] cannot be null")
    private String reference;
    
    @NotNull(message = "Field [description] cannot be null")
    private String description;
    
    @ValidZonedDateTime(message = "Field [created_date] must be a valid ISO-8601 time and date format")
    private String createdDate;

    @ValidZonedDateTime(message = "Field [authorised_date] must be a valid ISO-8601 time and date format")
    private String authorisedDate;
    
    @NotNull(message = "Field [processor_id] cannot be null")
    private String processorId;
    
    @NotNull(message = "Field [provider_id] cannot be null")
    private String providerId;
    
    private String authCode;

    @NotNull(message = "Field [payment_outcome] cannot be null")
    @Valid
    private PaymentOutcome paymentOutcome;
    
    @ValidCardBrand(message = "Field [card_type] must be either master-card, visa, maestro, diners-club, american-express or jcb")
    private String cardType;
    
    private String nameOnCard;
    
    private String emailAddress;
    
    private CardExpiryDate cardExpiry;

    @ValidCardLastFourDigits(message = "Field [last_four_digits] must be exactly 4 digits")
    private String lastFourDigits;

    @ValidCardFirstSixDigits(message = "Field [first_six_digits] must be exactly 6 digits")
    private String firstSixDigits;
    
    private String telephoneNumber;

    public TelephoneChargeCreateRequest() {
        // To enable Jackson serialisation we need a default constructor
    }
    
    public TelephoneChargeCreateRequest(Builder builder) {
        this.amount = builder.amount;
        this.reference = builder.reference;
        this.description = builder.description;
        this.createdDate = builder.createdDate;
        this.authorisedDate = builder.authorisedDate;
        this.processorId = builder.processorId;
        this.providerId = builder.providerId;
        this.authCode = builder.authCode;
        this.paymentOutcome = builder.paymentOutcome;
        this.cardType = builder.cardType;
        this.nameOnCard = builder.nameOnCard;
        this.emailAddress = builder.emailAddress;
        this.cardExpiry = builder.cardExpiry;
        this.cardType = builder.cardType;
        this.lastFourDigits = builder.lastFourDigits;
        this.firstSixDigits = builder.firstSixDigits;
        this.telephoneNumber = builder.telephoneNumber;
    }
    
    public Long getAmount() {
        return amount;
    }

    public String getReference() {
        return reference;
    }

    public String getDescription() {
        return description;
    }

    public Optional<String> getCreatedDate() {
        return Optional.ofNullable(createdDate);
    }

    public Optional<String> getAuthorisedDate() {
        return Optional.ofNullable(authorisedDate);
    }

    public String getProcessorId() {
        return processorId;
    }

    public String getProviderId() {
        return providerId;
    }

    public Optional<String> getAuthCode() {
        return Optional.ofNullable(authCode);
    }

    public PaymentOutcome getPaymentOutcome() {
        return paymentOutcome;
    }

    public Optional<String> getCardType() {
        return Optional.ofNullable(cardType);
    }

    public Optional<String> getNameOnCard() {
        return Optional.ofNullable(nameOnCard);
    }

    public Optional<String> getEmailAddress() {
        return Optional.ofNullable(emailAddress);
    }

    public Optional<CardExpiryDate> getCardExpiry() {
        return Optional.ofNullable(cardExpiry);
    }

    public Optional<String> getLastFourDigits() {
        return Optional.ofNullable(lastFourDigits);
    }

    public Optional<String> getFirstSixDigits() {
        return Optional.ofNullable(firstSixDigits);
    }

    public Optional<String> getTelephoneNumber() {
        return Optional.ofNullable(telephoneNumber);
    }
    
    public static class Builder {
        private Long amount;

        private String reference;

        private String description;

        private String createdDate;

        private String authorisedDate;

        private String processorId;

        private String providerId;

        private String authCode;

        private PaymentOutcome paymentOutcome;

        private String cardType;

        private String nameOnCard;

        private String emailAddress;

        private CardExpiryDate cardExpiry;

        private String lastFourDigits;

        private String firstSixDigits;

        private String telephoneNumber;

        public Builder withAmount(Long amount) {
            this.amount = amount;
            return this;
        }

        public Builder withReference(String reference) {
            this.reference = reference;
            return this;
        }
        
        public Builder withDescription(String description) {
            this.description = description;
            return this;
        }
        
        public Builder withCreatedDate(String createdDate) {
            this.createdDate = createdDate;
            return this;
        }
        
        public Builder withAuthorisedDate(String authorisedDate) {
            this.authorisedDate = authorisedDate;
            return this;
        }
        
        public Builder withProcessorId(String processorId) {
            this.processorId = processorId;
            return this;
        }
        
        public Builder withProviderId(String providerId) {
            this.providerId = providerId;
            return this;
        }
        
        public Builder withAuthCode(String authCode) {
            this.authCode = authCode;
            return this;
        }
        
        public Builder withPaymentOutcome(PaymentOutcome paymentOutcome) {
            this.paymentOutcome = paymentOutcome;
            return this;
        }
        
        public Builder withCardType(String cardType) {
            this.cardType = cardType;
            return this;
        }
        
        public Builder withNameOnCard(String nameOnCard) {
            this.nameOnCard = nameOnCard;
            return this;
        }
        
        public Builder withEmailAddress(String emailAddress) {
            this.emailAddress = emailAddress;
            return this;
        }
        
        public Builder withCardExpiry(CardExpiryDate cardExpiry) {
            this.cardExpiry = cardExpiry;
            return this;
        }
        
        public Builder withLastFourDigits(String lastFourDigits) {
            this.lastFourDigits = lastFourDigits;
            return this;
        }
        
        public Builder withFirstSixDigits(String firstSixDigits) {
            this.firstSixDigits = firstSixDigits;
            return this;
        }
        
        public Builder withTelephoneNumber(String telephoneNumber) {
            this.telephoneNumber = telephoneNumber;
            return this;
        }
        
        public TelephoneChargeCreateRequest build() {
            return new TelephoneChargeCreateRequest(this);
        }
    }
}
