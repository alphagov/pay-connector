package uk.gov.pay.connector.paritycheck;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class LedgerTransaction {

    private String transactionId;
    private Long amount;
    private String description;
    private String reference;
    private String email;
    private boolean delayedCapture;
    private Long corporateCardSurcharge;
    private Long totalAmount;
    private Long fee;
    private Long netAmount;
    private String createdDate;
    private TransactionState state;
    private String language;
    private String returnUrl;

    public Long getAmount() {
        return amount;
    }
    
    public String getLanguage() { 
        return language;
    }

    public String getDescription() {
        return description;
    }

    public String getReference() {
        return reference;
    }

    public String getEmail() {
        return email;
    }

    public boolean getDelayedCapture() {
        return delayedCapture;
    }

    public Long getCorporateCardSurcharge() {
        return corporateCardSurcharge;
    }

    public Long getTotalAmount() {
        return totalAmount;
    }

    public Long getFee() {
        return fee;
    }

    public Long getNetAmount() {
        return netAmount;
    }

    public String getCreatedDate() {
        return createdDate;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public TransactionState getState() {
        return state;
    }

    public void setState(TransactionState transactionState) {
        this.state = transactionState;
    }

    public String getReturnUrl() {
        return returnUrl;
    }
}
