package uk.gov.pay.connector.charge.model.telephone;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class TelephoneChargeResponse {

    private Long amount; // Yes

    private String reference; // Yes

    private String description; // Yes

    private String createdDate; // Yes

    private String authorisedDate; // Yes

    private String processorId; // Yes

    private String providerId; // Yes

    private String authCode; // Yes

    private PaymentOutcome paymentOutcome; // Yes

    private String cardType; // In PersistedCard

    private String nameOnCard; // In PersistedCard

    private String emailAddress; // Yes

    private String cardExpiry; // In PersistedCard

    private String lastFourDigits; // In PersistedCard

    private String firstSixDigits; // In PersistedCard

    private String telephoneNumber; // Yes

    private String paymentId; // Store as charge_id?

    private State state; // ExternalTransactionState

    public TelephoneChargeResponse() {
        // For Jackson
    }
    
    private TelephoneChargeResponse(ChargeBuilder chargeBuilder) {
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
        this.paymentId = chargeBuilder.paymentId;
        this.state = chargeBuilder.state;
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

    public String getPaymentId() { return paymentId; }

    public State getState() {
        return state;
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
        private String paymentId;
        private State state;

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

        public ChargeBuilder paymentId(String paymentId) {
            this.paymentId = paymentId;
            return this;
        }

        public ChargeBuilder state(State state) {
            this.state = state;
            return this;
        }
        
        public TelephoneChargeResponse build() {
            return new TelephoneChargeResponse(this);
        }
        
    }
    
}
