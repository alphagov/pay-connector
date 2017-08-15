package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.CardDetailsEntity;

import java.time.ZonedDateTime;

public class TransactionDto {

    private final String externalId;
    private final String reference;
    private final String description;
    private final String status;
    private final String email;
    private final long gatewayAccountId;
    private final String gatewayTransactionId;
    private final ZonedDateTime createdDate;
    private final String transactionType;
    private final CardDetailsEntity cardDetails;
    private final long amount;

    public TransactionDto(String transactionType, String externalId,
                          String reference,
                          String description, String status,
                          String email,
                          long gatewayAccountId,
                          String gatewayTransactionId,
                          ZonedDateTime createdDate,
                          CardDetailsEntity cardDetails,
                          long amount) {
        this.externalId = externalId;
        this.reference = reference;
        this.description = description;
        this.status = status;
        this.email = email;
        this.gatewayAccountId = gatewayAccountId;
        this.gatewayTransactionId = gatewayTransactionId;
        this.createdDate = createdDate;
        this.transactionType = transactionType;
        this.cardDetails = cardDetails;
        this.amount = amount;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getReference() {
        return reference;
    }

    public String getDescription() {
        return description;
    }

    public String getStatus() {
        return status;
    }

    public String getEmail() {
        return email;
    }

    public long getGatewayAccountId() {
        return gatewayAccountId;
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    public ZonedDateTime getCreatedDate() {
        return createdDate;
    }

    public CardDetailsEntity getCardDetails() {
        return cardDetails;
    }

    public long getAmount() {
        return amount;
    }
}
