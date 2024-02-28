package uk.gov.pay.connector.events.eventdetails.charge;

import uk.gov.pay.connector.cardtype.model.domain.CardBrandLabelEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.util.Objects;
import java.util.Optional;

public class PaymentDetailsSubmittedByAPIEventDetails extends EventDetails {

    private final String cardType;
    private final String cardBrand;
    private final String cardBrandLabel;
    private final String firstDigitsCardNumber;
    private final String lastDigitsCardNumber;
    private final String gatewayTransactionId;
    private final String cardholderName;
    private final String expiryDate;

    private PaymentDetailsSubmittedByAPIEventDetails(Builder builder) {
        this.cardType = builder.cardType;
        this.cardBrand = builder.cardBrand;
        this.cardBrandLabel = builder.cardBrandLabel;
        this.gatewayTransactionId = builder.gatewayTransactionId;
        this.firstDigitsCardNumber = builder.firstDigitsCardNumber;
        this.lastDigitsCardNumber = builder.lastDigitsCardNumber;
        this.cardholderName = builder.cardholderName;
        this.expiryDate = builder.expiryDate;
    }

    public static PaymentDetailsSubmittedByAPIEventDetails from(ChargeEntity charge) {
        Builder builder = new Builder()
                .withGatewayTransactionId(charge.getGatewayTransactionId());

        Optional.ofNullable(charge.getChargeCardDetails())
                .ifPresent(cardDetails ->
                        builder.withCardType(Optional.ofNullable(cardDetails.getCardDetails().getCardType()).map(Enum::toString).orElse(null))
                                .withCardBrand(cardDetails.getCardDetails().getCardBrand())
                                .withCardBrandLabel(cardDetails.getCardDetails().getCardTypeDetails().map(CardBrandLabelEntity::getLabel).orElse(null))
                                .withFirstDigitsCardNumber(cardDetails.getCardDetails().getFirstDigitsCardNumber().toString())
                                .withLastDigitsCardNumber(cardDetails.getCardDetails().getLastDigitsCardNumber().toString())
                                .withCardholderName(cardDetails.getCardDetails().getCardHolderName())
                                .withExpiryDate(cardDetails.getCardDetails().getExpiryDate().toString())
                );

        return builder.build();
    }

    public String getCardType() {
        return cardType;
    }

    public String getCardBrand() {
        return cardBrand;
    }

    public String getCardBrandLabel() {
        return cardBrandLabel;
    }

    public String getFirstDigitsCardNumber() {
        return firstDigitsCardNumber;
    }

    public String getLastDigitsCardNumber() {
        return lastDigitsCardNumber;
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    public String getCardholderName() {
        return cardholderName;
    }

    public String getExpiryDate() {
        return expiryDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentDetailsSubmittedByAPIEventDetails that = (PaymentDetailsSubmittedByAPIEventDetails) o;
        return Objects.equals(cardType, that.cardType) &&
                Objects.equals(cardBrand, that.cardBrand) &&
                Objects.equals(firstDigitsCardNumber, that.firstDigitsCardNumber) &&
                Objects.equals(lastDigitsCardNumber, that.lastDigitsCardNumber) &&
                Objects.equals(gatewayTransactionId, that.gatewayTransactionId) &&
                Objects.equals(cardholderName, that.cardholderName) &&
                Objects.equals(expiryDate, that.expiryDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cardType, cardBrand, firstDigitsCardNumber, lastDigitsCardNumber,
                gatewayTransactionId, cardholderName, expiryDate);
    }

    private static class Builder {
        private String cardType;
        private String cardBrand;
        private String cardBrandLabel;
        private String gatewayTransactionId;
        private String firstDigitsCardNumber;
        private String lastDigitsCardNumber;
        private String cardholderName;
        private String expiryDate;

        Builder withCardType(String cardType) {
            this.cardType = cardType;
            return this;
        }

        Builder withCardBrand(String cardBrand) {
            this.cardBrand = cardBrand;
            return this;
        }

        Builder withCardBrandLabel(String cardBrandLabel) {
            this.cardBrandLabel = cardBrandLabel;
            return this;
        }

        Builder withGatewayTransactionId(String gatewayTransactionId) {
            this.gatewayTransactionId = gatewayTransactionId;
            return this;
        }

        Builder withFirstDigitsCardNumber(String firstDigitsCardNumber) {
            this.firstDigitsCardNumber = firstDigitsCardNumber;
            return this;
        }

        Builder withLastDigitsCardNumber(String lastDigitsCardNumber) {
            this.lastDigitsCardNumber = lastDigitsCardNumber;
            return this;
        }

        Builder withCardholderName(String cardholderName) {
            this.cardholderName = cardholderName;
            return this;
        }

        Builder withExpiryDate(String expiryDate) {
            this.expiryDate = expiryDate;
            return this;
        }

        PaymentDetailsSubmittedByAPIEventDetails build() {
            return new PaymentDetailsSubmittedByAPIEventDetails(this);
        }
    }
}
