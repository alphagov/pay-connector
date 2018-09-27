package uk.gov.pay.connector.model.domain;

import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.sql.Timestamp;
import java.time.ZonedDateTime;

public final class TransactionBuilder {
    private Long chargeId = 1L;
    private String externalId = RandomIdGenerator.newId();
    private String reference = "Test reference";
    private String description = "Test description";
    private String status = "CREATED";
    private String email = "test@example.com";
    private long gatewayAccountId = 1L;
    private String gatewayTransactionId = "gateway-transaction-id";
    private ZonedDateTime createdDate = ZonedDateTime.now();
    private boolean delayedCapture = false;
    private Long corporateSurcharge = null;
    private String transactionType = "charge";
    private String cardBrand = "VISA";
    private String cardBrandLabel = "DEBIT";
    private String cardHolderName = "Mr Test";
    private String expiryDate = "12/99";
    private String lastDigitsCardNumber;
    private String firstDigitsCardNumber;
    private String userExternalId = RandomIdGenerator.newId();
    private String addressCity = "Test City";
    private String addressCountry = "Test Country";
    private String addressCounty = "Test County";
    private String addressLine1 = "Address Line 1";
    private String addressLine2 = "Address Line 2";
    private String addressPostcode = "Test Postcode";
    private long amount = 100L;
    private SupportedLanguage language = SupportedLanguage.ENGLISH;

    private TransactionBuilder() {
    }

    public static TransactionBuilder aTransaction() {
        return new TransactionBuilder();
    }

    public TransactionBuilder withChargeId(Long chargeId) {
        this.chargeId = chargeId;
        return this;
    }

    public TransactionBuilder withExternalId(String externalId) {
        this.externalId = externalId;
        return this;
    }

    public TransactionBuilder withReference(String reference) {
        this.reference = reference;
        return this;
    }

    public TransactionBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    public TransactionBuilder withStatus(String status) {
        this.status = status;
        return this;
    }

    public TransactionBuilder withEmail(String email) {
        this.email = email;
        return this;
    }

    public TransactionBuilder withGatewayAccountId(long gatewayAccountId) {
        this.gatewayAccountId = gatewayAccountId;
        return this;
    }

    public TransactionBuilder withGatewayTransactionId(String gatewayTransactionId) {
        this.gatewayTransactionId = gatewayTransactionId;
        return this;
    }

    public TransactionBuilder withDelayedCapture(boolean delayedCapture) {
        this.delayedCapture = delayedCapture;
        return this;
    }

    public TransactionBuilder withCorporateSurcharge(Long corporateSurcharge) {
        this.corporateSurcharge = corporateSurcharge;
        return this;
    }

    public TransactionBuilder withTransactionType(String transactionType) {
        this.transactionType = transactionType;
        return this;
    }

    public TransactionBuilder withCardBrand(String cardBrand) {
        this.cardBrand = cardBrand;
        return this;
    }

    public TransactionBuilder withCardBrandLabel(String cardBrandLabel) {
        this.cardBrandLabel = cardBrandLabel;
        return this;
    }

    public TransactionBuilder withCardHolderName(String cardHolderName) {
        this.cardHolderName = cardHolderName;
        return this;
    }

    public TransactionBuilder withExpiryDate(String expiryDate) {
        this.expiryDate = expiryDate;
        return this;
    }

    public TransactionBuilder withUserExternalId(String userExternalId) {
        this.userExternalId = userExternalId;
        return this;
    }

    public TransactionBuilder withAddressCity(String addressCity) {
        this.addressCity = addressCity;
        return this;
    }

    public TransactionBuilder withAddressCountry(String addressCountry) {
        this.addressCountry = addressCountry;
        return this;
    }

    public TransactionBuilder withAddressCounty(String addressCounty) {
        this.addressCounty = addressCounty;
        return this;
    }

    public TransactionBuilder withAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
        return this;
    }

    public TransactionBuilder withAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
        return this;
    }

    public TransactionBuilder withAddressPostcode(String addressPostcode) {
        this.addressPostcode = addressPostcode;
        return this;
    }

    public TransactionBuilder withAmount(long amount) {
        this.amount = amount;
        return this;
    }

    public TransactionBuilder withSupportedLanguage(SupportedLanguage language) {
        this.language = language;
        return this;
    }

    public TransactionBuilder withCreatedDate(ZonedDateTime createdDate) {
        this.createdDate = createdDate;
        return this;
    }

    public TransactionBuilder withLastDigitsCardNumber(String cardNumber) {
        this.lastDigitsCardNumber = cardNumber;
        return this;
    }

    public TransactionBuilder withFirstDigitsCardNumber(String cardNumber) {
        this.firstDigitsCardNumber = cardNumber;
        return this;
    }

    public Transaction build() {
        return new Transaction(transactionType, chargeId, externalId, reference, description, status, email,
                gatewayAccountId, gatewayTransactionId, Timestamp.valueOf(createdDate.toLocalDateTime()), cardBrand, cardBrandLabel,
                cardHolderName, expiryDate, lastDigitsCardNumber, firstDigitsCardNumber, userExternalId,
                addressCity, addressCountry, addressCounty, addressLine1, addressLine2, addressPostcode,
                amount, language.toString(), delayedCapture, corporateSurcharge);
    }
}
