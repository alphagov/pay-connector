package uk.gov.pay.connector.events.eventdetails.charge;

import uk.gov.pay.commons.model.charge.ExternalMetadata;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.util.Map;
import java.util.Objects;

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
    private final String cardExpiryDate;
    private final String cardBrand;
    private final Map<String, Object> externalMetadata;


    private PaymentNotificationCreatedEventDetails(Long gatewayAccountId, Long amount, String reference,
                                                   String description, String gatewayTransactionId,
                                                   String firstDigitsCardNumber, String lastDigitsCardNumber,
                                                   String cardholderName, String email, String cardExpiryDate,
                                                   String cardBrand, Map<String, Object> externalMetadata) {
        this.gatewayAccountId = gatewayAccountId;
        this.amount = amount;
        this.reference = reference;
        this.description = description;
        this.gatewayTransactionId = gatewayTransactionId;
        this.firstDigitsCardNumber = firstDigitsCardNumber;
        this.lastDigitsCardNumber = lastDigitsCardNumber;
        this.cardholderName = cardholderName;
        this.email = email;
        this.cardExpiryDate = cardExpiryDate;
        this.cardBrand = cardBrand;
        this.externalMetadata = externalMetadata;
    }

    public static PaymentNotificationCreatedEventDetails from(ChargeEntity charge) {
        return new PaymentNotificationCreatedEventDetails(charge.getGatewayAccount().getId(), charge.getAmount(),
                charge.getReference().toString(), charge.getDescription(), charge.getGatewayTransactionId(),
                charge.getCardDetails().getFirstDigitsCardNumber().toString(), charge.getCardDetails().getLastDigitsCardNumber().toString(),
                charge.getCardDetails().getCardHolderName(), charge.getEmail(), charge.getCardDetails().getExpiryDate(), charge.getCardDetails().getCardBrand(),
                charge.getExternalMetadata().map(ExternalMetadata::getMetadata).orElse(null));
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
                Objects.equals(cardExpiryDate, that.cardExpiryDate) &&
                Objects.equals(cardBrand, that.cardBrand) &&
                Objects.equals(externalMetadata, that.externalMetadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gatewayAccountId, amount, reference, description, gatewayTransactionId,
                firstDigitsCardNumber, lastDigitsCardNumber, cardholderName, email, cardExpiryDate,
                cardBrand, externalMetadata);
    }
}
