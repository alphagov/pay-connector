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
    private final String cardBrand;
    private final String cardHolderName;
    private final String expiryDate;
    private final String lastDigitsCardNumber;
    private final String addressCity;
    private final String addressCountry;
    private final String addressCounty;
    private final String addressLine1;
    private final String addressLine2;
    private final String addressPostcode;
    private final long amount;

    public TransactionDto(String transactionType, String externalId,
                          String reference,
                          String description, String status,
                          String email,
                          long gatewayAccountId,
                          String gatewayTransactionId,
                          ZonedDateTime createdDate,
                          String cardBrand, String cardHolderName, String expiryDate, String lastDigitsCardNumber, String addressCity, String addressCountry, String addressCounty, String addressLine1, String addressLine2, String addressPostcode,
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
        this.cardBrand = cardBrand;
        this.cardHolderName = cardHolderName;
        this.expiryDate = expiryDate;
        this.lastDigitsCardNumber = lastDigitsCardNumber;
        this.addressCity = addressCity;
        this.addressCountry = addressCountry;
        this.addressCounty = addressCounty;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.addressPostcode = addressPostcode;
        this.amount = amount;
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

    public String getTransactionType() {
        return transactionType;
    }

    public String getCardBrand() {
        return cardBrand;
    }

    public String getCardHolderName() {
        return cardHolderName;
    }

    public String getExpiryDate() {
        return expiryDate;
    }

    public String getLastDigitsCardNumber() {
        return lastDigitsCardNumber;
    }

    public String getAddressCity() {
        return addressCity;
    }

    public String getAddressCountry() {
        return addressCountry;
    }

    public String getAddressCounty() {
        return addressCounty;
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

    public long getAmount() {
        return amount;
    }
}
