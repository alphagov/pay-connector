package uk.gov.pay.connector.charge.model.telephone;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import uk.gov.pay.connector.charge.validation.telephone.ValidCardExpiryDate;
import uk.gov.pay.connector.charge.validation.telephone.ValidCardFirstSixDigits;
import uk.gov.pay.connector.charge.validation.telephone.ValidCardLastFourDigits;
import uk.gov.pay.connector.charge.validation.telephone.ValidCardType;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class TelephoneChargeCreateRequest {
    
    @NotNull
    private Long amount;
    
    @NotNull
    private String reference;
    
    @NotNull
    private String description;
    
    private String createdDate;
    
    private String authorisedDate;
    
    @NotNull
    private String processorId;
    
    @NotNull
    private String providerId;
    
    private String authCode;

    @NotNull
    @Valid
    private PaymentOutcome paymentOutcome;
    
    @NotNull
    @ValidCardType
    private String cardType;
    
    private String nameOnCard;
    
    private String emailAddress;

    @NotNull
    @ValidCardExpiryDate
    private String cardExpiry;

    @NotNull
    @ValidCardLastFourDigits
    private String lastFourDigits;

    @NotNull
    @ValidCardFirstSixDigits
    private String firstSixDigits;
    
    private String telephoneNumber;

    public TelephoneChargeCreateRequest() {
        // To enable Jackson serialisation we need a default constructor
    }
    
    public TelephoneChargeCreateRequest(ChargeBuilder chargeBuilder) {
        this.amount = chargeBuilder.amount;
        this.reference = chargeBuilder.reference;
        this.description = chargeBuilder.description;
        this.createdDate = chargeBuilder.createdDate;
        this.authorisedDate = chargeBuilder.authorisedDate;
        this.processorId = chargeBuilder.processorId;
        this.providerId = chargeBuilder.providerId;
        this.authCode = chargeBuilder.authCode;
        this.paymentOutcome = chargeBuilder.paymentOutcome;
        this.cardType = chargeBuilder.cardType;
        this.nameOnCard = chargeBuilder.nameOnCard;
        this.emailAddress = chargeBuilder.emailAddress;
        this.cardExpiry = chargeBuilder.cardExpiry;
        this.lastFourDigits = chargeBuilder.lastFourDigits;
        this.firstSixDigits = chargeBuilder.firstSixDigits;
        this.telephoneNumber = chargeBuilder.telephoneNumber;
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

    public String getCreatedDate() {
        return createdDate;
    }

    public String getAuthorisedDate() {
        return authorisedDate;
    }

    public String getProcessorId() {
        return processorId;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getAuthCode() {
        return authCode;
    }

    public PaymentOutcome getPaymentOutcome() {
        return paymentOutcome;
    }

    public String getCardType() {
        return cardType;
    }

    public String getNameOnCard() {
        return nameOnCard;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public String getCardExpiry() {
        return cardExpiry;
    }

    public String getLastFourDigits() {
        return lastFourDigits;
    }

    public String getFirstSixDigits() {
        return firstSixDigits;
    }

    public String getTelephoneNumber() {
        return telephoneNumber;
    }
    
    public static class ChargeBuilder {
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

        private String cardExpiry;

        private String lastFourDigits;

        private String firstSixDigits;

        private String telephoneNumber;
        
        public ChargeBuilder amount(Long amount) {
            this.amount = amount;
            return this;
        }

        public ChargeBuilder reference(String reference) {
            this.reference = reference;
            return this;
        }
        
        public ChargeBuilder description(String description) {
            this.description = description;
            return this;
        }
        
        public ChargeBuilder createdDate(String createdDate) {
            this.createdDate = createdDate;
            return this;
        }
        
        public ChargeBuilder authorisedDate(String authorisedDate) {
            this.authorisedDate = authorisedDate;
            return this;
        }
        
        public ChargeBuilder processorId(String processorId) {
            this.processorId = processorId;
            return this;
        }
        
        public ChargeBuilder providerId(String providerId) {
            this.providerId = providerId;
            return this;
        }
        
        public ChargeBuilder authCode(String authCode) {
            this.authCode = authCode;
            return this;
        }
        
        public ChargeBuilder paymentOutcome(PaymentOutcome paymentOutcome) {
            this.paymentOutcome = paymentOutcome;
            return this;
        }
        
        public ChargeBuilder cardType(String cardType) {
            this.cardType = cardType;
            return this;
        }
        
        public ChargeBuilder nameOnCard(String nameOnCard) {
            this.nameOnCard = nameOnCard;
            return this;
        }
        
        public ChargeBuilder emailAddress(String emailAddress) {
            this.emailAddress = emailAddress;
            return this;
        }
        
        public ChargeBuilder cardExpiry(String cardExpiry) {
            this.cardExpiry = cardExpiry;
            return this;
        }
        
        public ChargeBuilder lastFourDigits(String lastFourDigits) {
            this.lastFourDigits = lastFourDigits;
            return this;
        }
        
        public ChargeBuilder firstSixDigits(String firstSixDigits) {
            this.firstSixDigits = firstSixDigits;
            return this;
        }
        
        public ChargeBuilder telephoneNumber(String telephoneNumber) {
            this.telephoneNumber = telephoneNumber;
            return this;
        }
        
        public TelephoneChargeCreateRequest build() {
            return new TelephoneChargeCreateRequest(this);
        }
    }
}
