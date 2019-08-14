package uk.gov.pay.connector.charge.model.domain;

import org.eclipse.persistence.annotations.ReadOnly;
import org.postgresql.util.PGobject;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.commons.model.charge.ExternalMetadata;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumberConverter;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumberConverter;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.util.ExternalMetadataConverter;
import uk.gov.pay.connector.common.model.domain.UTCDateTimeConverter;
import uk.gov.pay.connector.wallets.WalletType;

import javax.persistence.ColumnResult;
import javax.persistence.ConstructorResult;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.SqlResultSetMapping;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.Optional;

@SqlResultSetMapping(
        name = "TransactionMapping",
        classes = @ConstructorResult(
                targetClass = Transaction.class,
                columns = {
                        @ColumnResult(name = "transaction_type", type = String.class),
                        @ColumnResult(name = "charge_id", type = Long.class),
                        @ColumnResult(name = "external_id", type = String.class),
                        @ColumnResult(name = "reference", type = String.class),
                        @ColumnResult(name = "description", type = String.class),
                        @ColumnResult(name = "status", type = String.class),
                        @ColumnResult(name = "email", type = String.class),
                        @ColumnResult(name = "gateway_account_id", type = Long.class),
                        @ColumnResult(name = "gateway_transaction_id", type = String.class),
                        @ColumnResult(name = "date_created", type = Timestamp.class),
                        @ColumnResult(name = "card_brand", type = String.class),
                        @ColumnResult(name = "card_brand_label", type = String.class),
                        @ColumnResult(name = "cardholder_name", type = String.class),
                        @ColumnResult(name = "expiry_date", type = String.class),
                        @ColumnResult(name = "last_digits_card_number", type = String.class),
                        @ColumnResult(name = "first_digits_card_number", type = String.class),
                        @ColumnResult(name = "user_external_id", type = String.class),
                        @ColumnResult(name = "address_city", type = String.class),
                        @ColumnResult(name = "address_country", type = String.class),
                        @ColumnResult(name = "address_county", type = String.class),
                        @ColumnResult(name = "address_line1", type = String.class),
                        @ColumnResult(name = "address_line2", type = String.class),
                        @ColumnResult(name = "address_postcode", type = String.class),
                        @ColumnResult(name = "amount", type = Long.class),
                        @ColumnResult(name = "language", type = String.class),
                        @ColumnResult(name = "delayed_capture", type = Boolean.class),
                        @ColumnResult(name = "corporate_surcharge", type = Long.class),
                        @ColumnResult(name = "wallet", type = String.class),
                        @ColumnResult(name = "fee_amount", type = Long.class),
                        @ColumnResult(name = "external_metadata", type = PGobject.class)
                }))
@Entity
@ReadOnly
public class Transaction implements Nettable {

    @Id
    private Long chargeId;
    private String externalId;
    private String reference;
    private String description;
    private String status;
    private String email;
    private long gatewayAccountId;
    private String gatewayTransactionId;
    private ZonedDateTime createdDate;
    private SupportedLanguage language;
    private boolean delayedCapture;
    private TransactionType transactionType;
    private String cardBrand;
    private String cardBrandLabel;
    private String cardHolderName;
    private String expiryDate;
    @Convert(converter = LastDigitsCardNumberConverter.class)
    private LastDigitsCardNumber lastDigitsCardNumber;
    @Convert(converter = FirstDigitsCardNumberConverter.class)
    private FirstDigitsCardNumber firstDigitsCardNumber;
    private String userExternalId;
    private String addressCity;
    private String addressCountry;
    private String addressCounty;
    private String addressLine1;
    private String addressLine2;
    private String addressPostcode;
    private long amount;
    private Long corporateSurcharge;
    private WalletType walletType;
    private Long feeAmount;
    @Convert(converter = ExternalMetadataConverter.class)
    private ExternalMetadata externalMetadata;

    public Transaction() {
    }

    public Transaction(String transactionType,
                       long chargeId,
                       String externalId,
                       String reference,
                       String description,
                       String status,
                       String email,
                       long gatewayAccountId,
                       String gatewayTransactionId,
                       Timestamp createdDate,
                       String cardBrand,
                       String cardBrandLabel,
                       String cardHolderName,
                       String expiryDate,
                       String lastDigitsCardNumber,
                       String firstDigitsCardNumber,
                       String userExternalId,
                       String addressCity,
                       String addressCountry,
                       String addressCounty,
                       String addressLine1,
                       String addressLine2,
                       String addressPostcode,
                       long amount,
                       String language,
                       boolean delayedCapture,
                       Long corporateSurcharge,
                       String walletType,
                       Long feeAmount,
                       PGobject externalMetadata) {
        this.chargeId = chargeId;
        this.externalId = externalId;
        this.reference = reference;
        this.description = description;
        this.status = status;
        this.email = email;
        this.gatewayAccountId = gatewayAccountId;
        this.gatewayTransactionId = gatewayTransactionId;
        this.createdDate = new UTCDateTimeConverter().convertToEntityAttribute(createdDate);
        this.transactionType = TransactionType.fromString(transactionType);
        this.cardBrand = cardBrand;
        this.userExternalId = userExternalId;
        this.cardBrandLabel = cardBrandLabel;
        this.cardHolderName = cardHolderName;
        this.expiryDate = expiryDate;
        this.lastDigitsCardNumber = new LastDigitsCardNumberConverter().convertToEntityAttribute(lastDigitsCardNumber);
        this.firstDigitsCardNumber = new FirstDigitsCardNumberConverter().convertToEntityAttribute(firstDigitsCardNumber);
        this.addressCity = addressCity;
        this.addressCountry = addressCountry;
        this.addressCounty = addressCounty;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.addressPostcode = addressPostcode;
        this.amount = amount;
        this.language = SupportedLanguage.fromIso639AlphaTwoCode(language);
        this.delayedCapture = delayedCapture;
        this.corporateSurcharge = corporateSurcharge;
        this.walletType = walletType == null ? null : WalletType.valueOf(walletType);
        this.feeAmount = feeAmount;
        this.externalMetadata = new ExternalMetadataConverter().convertToEntityAttribute(externalMetadata);
    }

    public Long getChargeId() {
        return chargeId;
    }

    public String getExternalId() {
        return externalId;
    }

    public ServicePaymentReference getReference() {
        return ServicePaymentReference.of(reference);
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

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public String getCardBrand() {
        return cardBrand;
    }

    public String getCardBrandLabel() {
        return cardBrandLabel;
    }

    public String getCardHolderName() {
        return cardHolderName;
    }

    public String getExpiryDate() {
        return expiryDate;
    }

    public LastDigitsCardNumber getLastDigitsCardNumber() {
        return lastDigitsCardNumber;
    }

    public FirstDigitsCardNumber getFirstDigitsCardNumber() {
        return firstDigitsCardNumber;
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

    public Long getAmount() {
        return amount;
    }

    public SupportedLanguage getLanguage() {
        return language;
    }

    public boolean isDelayedCapture() {
        return delayedCapture;
    }

    public String getUserExternalId() {
        return userExternalId;
    }
    public Optional<Long> getCorporateSurcharge() {
        return Optional.ofNullable(corporateSurcharge);
    }

    public WalletType getWalletType() {
        return walletType;
    }

    public Optional<Long> getFeeAmount() {
        return Optional.ofNullable(feeAmount);
    }

    public Optional<ExternalMetadata> getExternalMetadata() {
        return Optional.ofNullable(externalMetadata);
    }
}
