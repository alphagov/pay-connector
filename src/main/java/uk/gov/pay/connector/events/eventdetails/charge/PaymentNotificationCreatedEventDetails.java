package uk.gov.pay.connector.events.eventdetails.charge;

import uk.gov.pay.commons.model.CardExpiryDate;
import uk.gov.pay.commons.model.Source;
import uk.gov.pay.commons.model.charge.ExternalMetadata;
import uk.gov.pay.connector.cardtype.model.domain.CardBrandLabelEntity;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class PaymentNotificationCreatedEventDetails extends EventDetails {

    private final Long gatewayAccountId;
    private final Long amount;
    private final String reference;
    private final String description;
    private final String gatewayTransactionId;
    private final String firstDigitsCardNumber;
    private final String lastDigitsCardNumber;
    private final String cardholderName;
    private final String email;
    private final String expiryDate;
    private final String cardBrand;
    private final String cardBrandLabel;
    private final boolean live;
    private final String paymentProvider;
    private final Map<String, Object> externalMetadata;
    private final String language;
    private Source source;

    private PaymentNotificationCreatedEventDetails(Long gatewayAccountId, Long amount, String reference,
                                                   String description, String gatewayTransactionId,
                                                   String firstDigitsCardNumber, String lastDigitsCardNumber,
                                                   String cardholderName, String email, String expiryDate,
                                                   String cardBrand, String cardBrandLabel, boolean live,
                                                   String paymentProvider, Map<String, Object> externalMetadata,
                                                   Source source, String language) {
        this.gatewayAccountId = gatewayAccountId;
        this.amount = amount;
        this.reference = reference;
        this.description = description;
        this.gatewayTransactionId = gatewayTransactionId;
        this.firstDigitsCardNumber = firstDigitsCardNumber;
        this.lastDigitsCardNumber = lastDigitsCardNumber;
        this.cardholderName = cardholderName;
        this.email = email;
        this.expiryDate = expiryDate;
        this.cardBrand = cardBrand;
        this.cardBrandLabel = cardBrandLabel;
        this.live = live;
        this.externalMetadata = externalMetadata;
        this.paymentProvider = paymentProvider;
        this.source = source;
        this.language = language;
    }

    public static PaymentNotificationCreatedEventDetails from(ChargeEntity charge) {
        Optional<CardDetailsEntity> cardDetails = Optional.ofNullable(charge.getCardDetails());
        return new PaymentNotificationCreatedEventDetails(charge.getGatewayAccount().getId(),
                charge.getAmount(),
                charge.getReference().toString(),
                charge.getDescription(),
                charge.getGatewayTransactionId(),
                cardDetails.map(CardDetailsEntity::getFirstDigitsCardNumber).map(FirstDigitsCardNumber::toString).orElse(null),
                cardDetails.map(CardDetailsEntity::getLastDigitsCardNumber).map(LastDigitsCardNumber::toString).orElse(null),
                cardDetails.map(CardDetailsEntity::getCardHolderName).orElse(null),
                charge.getEmail(),
                cardDetails.map(CardDetailsEntity::getExpiryDate).map(CardExpiryDate::toString).orElse(null),
                cardDetails.map(CardDetailsEntity::getCardBrand).orElse(null),
                cardDetails.flatMap(CardDetailsEntity::getCardTypeDetails).map(CardBrandLabelEntity::getLabel).orElse(null),
                charge.getGatewayAccount().isLive(),
                charge.getGatewayAccount().getGatewayName(),
                charge.getExternalMetadata().map(ExternalMetadata::getMetadata).orElse(null),
                charge.getSource(),
                charge.getLanguage().toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentNotificationCreatedEventDetails that = (PaymentNotificationCreatedEventDetails) o;
        return Objects.equals(gatewayAccountId, that.gatewayAccountId) &&
                Objects.equals(amount, that.amount) &&
                Objects.equals(reference, that.reference) &&
                Objects.equals(description, that.description) &&
                Objects.equals(gatewayTransactionId, that.gatewayTransactionId) &&
                Objects.equals(firstDigitsCardNumber, that.firstDigitsCardNumber) &&
                Objects.equals(lastDigitsCardNumber, that.lastDigitsCardNumber) &&
                Objects.equals(cardholderName, that.cardholderName) &&
                Objects.equals(email, that.email) &&
                Objects.equals(expiryDate, that.expiryDate) &&
                Objects.equals(cardBrand, that.cardBrand) &&
                Objects.equals(cardBrandLabel, that.cardBrandLabel) &&
                Objects.equals(live, that.live) &&
                Objects.equals(externalMetadata, that.externalMetadata) &&
                Objects.equals(source, that.source) &&
                Objects.equals(language, that.language);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gatewayAccountId, amount, reference, description, gatewayTransactionId,
                firstDigitsCardNumber, lastDigitsCardNumber, cardholderName, email, expiryDate,
                cardBrand, cardBrandLabel, live, externalMetadata, source, language);
    }

    public Long getGatewayAccountId() {
        return gatewayAccountId;
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

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    public String getFirstDigitsCardNumber() {
        return firstDigitsCardNumber;
    }

    public String getLastDigitsCardNumber() {
        return lastDigitsCardNumber;
    }

    public String getCardholderName() {
        return cardholderName;
    }

    public String getEmail() {
        return email;
    }

    public String getExpiryDate() {
        return expiryDate;
    }

    public String getCardBrand() {
        return cardBrand;
    }

    public String getCardBrandLabel() {
        return cardBrandLabel;
    }

    public boolean isLive() {
        return live;
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public Map<String, Object> getExternalMetadata() {
        return externalMetadata;
    }

    public Source getSource() {
        return source;
    }

    public String getLanguage() {
        return language;
    }
}
