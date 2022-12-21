package uk.gov.pay.connector.events.eventdetails.charge;

import uk.gov.pay.connector.cardtype.model.domain.CardBrandLabelEntity;
import uk.gov.pay.connector.charge.model.AddressEntity;
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

    private PaymentDetailsTakenFromPaymentInstrumentEventDetails(String cardType, String cardBrand, String cardBrandLabel, String firstDigitsCardNumber, String lastDigitsCardNumber, String cardholderName, String expiryDate, String addressLine1, String addressLine2, String addressPostcode, String addressCity, String addressCounty, String addressStateProvince, String addressCountry) {
        this.cardType = cardType;
        this.cardBrand = cardBrand;
        this.cardBrandLabel = cardBrandLabel;
        this.firstDigitsCardNumber = firstDigitsCardNumber;
        this.lastDigitsCardNumber = lastDigitsCardNumber;
        this.cardholderName = cardholderName;
        this.expiryDate = expiryDate;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.addressPostcode = addressPostcode;
        this.addressCity = addressCity;
        this.addressCounty = addressCounty;
        this.addressStateProvince = addressStateProvince;
        this.addressCountry = addressCountry;
    }

    public static PaymentDetailsTakenFromPaymentInstrumentEventDetails from(ChargeEntity charge) {
        var cardDetails = charge.getCardDetails();
        return new PaymentDetailsTakenFromPaymentInstrumentEventDetails(
                Optional.ofNullable(cardDetails.getCardType()).map(Enum::toString).orElse(null),
                cardDetails.getCardBrand(),
                cardDetails.getCardTypeDetails().map(CardBrandLabelEntity::getLabel).orElse(null),
                cardDetails.getFirstDigitsCardNumber().toString(),
                cardDetails.getLastDigitsCardNumber().toString(),
                cardDetails.getCardHolderName(),
                cardDetails.getExpiryDate().toString(),
                cardDetails.getBillingAddress().map(AddressEntity::getLine1).orElse(null),
                cardDetails.getBillingAddress().map(AddressEntity::getLine2).orElse(null),
                cardDetails.getBillingAddress().map(AddressEntity::getPostcode).orElse(null),
                cardDetails.getBillingAddress().map(AddressEntity::getCity).orElse(null),
                cardDetails.getBillingAddress().map(AddressEntity::getCounty).orElse(null),
                cardDetails.getBillingAddress().map(AddressEntity::getStateOrProvince).orElse(null),
                cardDetails.getBillingAddress().map(AddressEntity::getCountry).orElse(null)
        );
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
}
