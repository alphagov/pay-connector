package uk.gov.pay.connector.events.eventdetails.charge;

import uk.gov.pay.connector.cardtype.model.domain.CardBrandLabelEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.util.Objects;
import java.util.Optional;

public class PaymentDetailsTakenFromPaymentInstrumentEventDetails extends EventDetails {
    private final String cardType;
    private final String cardBrand;
    private final String cardBrandLabel;
    private final String firstDigitsCardNumber;
    private final String lastDigitsCardNumber;
    private final String cardholderName;
    private final String expiryDate;
    private final String addressLine1;
    private final String addressLine2;
    private final String addressPostcode;
    private final String addressCity;
    private final String addressCounty;
    private final String addressStateProvince;
    private final String addressCountry;
    private final String gatewayTransactionId;

    public PaymentDetailsTakenFromPaymentInstrumentEventDetails(PaymentDetailsTakenFromPaymentInstrumentEventDetailsBuilder builder) {
        this.cardType = builder.cardType;
        this.cardBrand = builder.cardBrand;
        this.cardBrandLabel = builder.cardBrandLabel;
        this.firstDigitsCardNumber = builder.firstDigitsCardNumber;
        this.lastDigitsCardNumber = builder.lastDigitsCardNumber;
        this.cardholderName = builder.cardholderName;
        this.expiryDate = builder.expiryDate;
        this.addressLine1 = builder.addressLine1;
        this.addressLine2 = builder.addressLine2;
        this.addressPostcode = builder.addressPostcode;
        this.addressCity = builder.addressCity;
        this.addressCounty = builder.addressCounty;
        this.addressStateProvince = builder.addressStateProvince;
        this.addressCountry = builder.addressCountry;
        this.gatewayTransactionId = builder.gatewayTransactionId;
    }

    public static PaymentDetailsTakenFromPaymentInstrumentEventDetails from(ChargeEntity charge) {
        var cardDetails = charge.getChargeCardDetails();
        var builder = new PaymentDetailsTakenFromPaymentInstrumentEventDetailsBuilder()
                .setGatewayTransactionId(charge.getGatewayTransactionId())
                .setCardType(Optional.ofNullable(cardDetails.getCardDetails().getCardType()).map(Enum::toString).orElse(null))
                .setCardBrand(cardDetails.getCardDetails().getCardBrand())
                .setCardBrandLabel(cardDetails.getCardDetails().getCardTypeDetails().map(CardBrandLabelEntity::getLabel).orElse(null))
                .setFirstDigitsCardNumber(cardDetails.getCardDetails().getFirstDigitsCardNumber().toString())
                .setLastDigitsCardNumber(cardDetails.getCardDetails().getLastDigitsCardNumber().toString())
                .setCardholderName(cardDetails.getCardDetails().getCardHolderName())
                .setExpiryDate(cardDetails.getCardDetails().getExpiryDate().toString());

        cardDetails.getCardDetails().getBillingAddress().ifPresent(billingAddress -> {
            builder.setAddressLine1(billingAddress.getLine1())
                    .setAddressLine2(billingAddress.getLine2())
                    .setAddressPostcode(billingAddress.getPostcode())
                    .setAddressCity(billingAddress.getCity())
                    .setAddressCounty(billingAddress.getCounty())
                    .setAddressStateProvince(billingAddress.getStateOrProvince())
                    .setAddressCountry(billingAddress.getCountry());
        });
        return builder.createPaymentDetailsTakenFromPaymentInstrumentEventDetails();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentDetailsTakenFromPaymentInstrumentEventDetails that = (PaymentDetailsTakenFromPaymentInstrumentEventDetails) o;
        return Objects.equals(cardType, that.cardType) &&
                Objects.equals(cardBrand, that.cardBrand) &&
                Objects.equals(firstDigitsCardNumber, that.firstDigitsCardNumber) &&
                Objects.equals(lastDigitsCardNumber, that.lastDigitsCardNumber) &&
                Objects.equals(cardholderName, that.cardholderName) &&
                Objects.equals(expiryDate, that.expiryDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cardType, cardBrand, firstDigitsCardNumber, lastDigitsCardNumber, cardholderName, expiryDate);
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

    public String getCardholderName() {
        return cardholderName;
    }

    public String getExpiryDate() {
        return expiryDate;
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

    public String getAddressCity() {
        return addressCity;
    }

    public String getAddressCounty() {
        return addressCounty;
    }

    public String getAddressStateProvince() {
        return addressStateProvince;
    }

    public String getAddressCountry() {
        return addressCountry;
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }
}
