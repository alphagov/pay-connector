package uk.gov.pay.connector.model.domain;

import org.eclipse.persistence.annotations.ReadOnly;

import javax.persistence.*;
import java.sql.Timestamp;
import java.time.ZonedDateTime;

@SqlResultSetMapping(
        name = "TransactionMapping",
        classes = @ConstructorResult(
                targetClass = Transaction.class,
                columns = {
                        @ColumnResult(name = "transaction_type", type = String.class),
                        @ColumnResult(name = "external_id", type = String.class),
                        @ColumnResult(name = "reference", type = String.class),
                        @ColumnResult(name = "description", type = String.class),
                        @ColumnResult(name = "status", type = String.class),
                        @ColumnResult(name = "email", type = String.class),
                        @ColumnResult(name = "gateway_account_id", type = Long.class),
                        @ColumnResult(name = "gateway_transaction_id", type = String.class),
                        @ColumnResult(name = "date_created", type = Timestamp.class),
                        @ColumnResult(name = "card_brand", type = String.class),
                        @ColumnResult(name = "cardholder_name", type = String.class),
                        @ColumnResult(name = "expiry_date", type = String.class),
                        @ColumnResult(name = "last_digits_card_number", type = String.class),
                        @ColumnResult(name = "address_city", type = String.class),
                        @ColumnResult(name = "address_country", type = String.class),
                        @ColumnResult(name = "address_county", type = String.class),
                        @ColumnResult(name = "address_line1", type = String.class),
                        @ColumnResult(name = "address_line2", type = String.class),
                        @ColumnResult(name = "address_postcode", type = String.class),
                        @ColumnResult(name = "amount", type = Long.class)}))
@Entity
@ReadOnly
public class Transaction {

    @Id
    private String externalId;
    private String reference;
    private String description;
    private String status;
    private String email;
    private long gatewayAccountId;
    private String gatewayTransactionId;
    private ZonedDateTime createdDate;

    @Id
    private String transactionType;
    private String cardBrand;
    private String cardHolderName;
    private String expiryDate;
    private String lastDigitsCardNumber;
    private String addressCity;
    private String addressCountry;
    private String addressCounty;
    private String addressLine1;
    private String addressLine2;
    private String addressPostcode;
    private long amount;

    public Transaction() {
    }

    public Transaction(String transactionType,
                       String externalId,
                       String reference,
                       String description,
                       String status,
                       String email,
                       long gatewayAccountId,
                       String gatewayTransactionId,
                       Timestamp createdDate,
                       String cardBrand,
                       String cardHolderName,
                       String expiryDate,
                       String lastDigitsCardNumber,
                       String addressCity,
                       String addressCountry,
                       String addressCounty,
                       String addressLine1,
                       String addressLine2,
                       String addressPostcode,
                       long amount) {
        this.externalId = externalId;
        this.reference = reference;
        this.description = description;
        this.status = status;
        this.email = email;
        this.gatewayAccountId = gatewayAccountId;
        this.gatewayTransactionId = gatewayTransactionId;
        this.createdDate = new UTCDateTimeConverter().convertToEntityAttribute(createdDate);
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
