package uk.gov.pay.connector.events.eventdetails.charge;

public class PaymentDetailsTakenFromPaymentInstrumentEventDetailsBuilder {
    String cardType;
    String cardBrand;
    String cardBrandLabel;
    String firstDigitsCardNumber;
    String lastDigitsCardNumber;
    String cardholderName;
    String expiryDate;
    String addressLine1;
    String addressLine2;
    String addressPostcode;
    String addressCity;
    String addressCounty;
    String addressStateProvince;
    String addressCountry;

    public PaymentDetailsTakenFromPaymentInstrumentEventDetailsBuilder setCardType(String cardType) {
        this.cardType = cardType;
        return this;
    }

    public PaymentDetailsTakenFromPaymentInstrumentEventDetailsBuilder setCardBrand(String cardBrand) {
        this.cardBrand = cardBrand;
        return this;
    }

    public PaymentDetailsTakenFromPaymentInstrumentEventDetailsBuilder setCardBrandLabel(String cardBrandLabel) {
        this.cardBrandLabel = cardBrandLabel;
        return this;
    }

    public PaymentDetailsTakenFromPaymentInstrumentEventDetailsBuilder setFirstDigitsCardNumber(String firstDigitsCardNumber) {
        this.firstDigitsCardNumber = firstDigitsCardNumber;
        return this;
    }

    public PaymentDetailsTakenFromPaymentInstrumentEventDetailsBuilder setLastDigitsCardNumber(String lastDigitsCardNumber) {
        this.lastDigitsCardNumber = lastDigitsCardNumber;
        return this;
    }

    public PaymentDetailsTakenFromPaymentInstrumentEventDetailsBuilder setCardholderName(String cardholderName) {
        this.cardholderName = cardholderName;
        return this;
    }

    public PaymentDetailsTakenFromPaymentInstrumentEventDetailsBuilder setExpiryDate(String expiryDate) {
        this.expiryDate = expiryDate;
        return this;
    }

    public PaymentDetailsTakenFromPaymentInstrumentEventDetailsBuilder setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
        return this;
    }

    public PaymentDetailsTakenFromPaymentInstrumentEventDetailsBuilder setAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
        return this;
    }

    public PaymentDetailsTakenFromPaymentInstrumentEventDetailsBuilder setAddressPostcode(String addressPostcode) {
        this.addressPostcode = addressPostcode;
        return this;
    }

    public PaymentDetailsTakenFromPaymentInstrumentEventDetailsBuilder setAddressCity(String addressCity) {
        this.addressCity = addressCity;
        return this;
    }

    public PaymentDetailsTakenFromPaymentInstrumentEventDetailsBuilder setAddressCounty(String addressCounty) {
        this.addressCounty = addressCounty;
        return this;
    }

    public PaymentDetailsTakenFromPaymentInstrumentEventDetailsBuilder setAddressStateProvince(String addressStateProvince) {
        this.addressStateProvince = addressStateProvince;
        return this;
    }

    public PaymentDetailsTakenFromPaymentInstrumentEventDetailsBuilder setAddressCountry(String addressCountry) {
        this.addressCountry = addressCountry;
        return this;
    }

    public PaymentDetailsTakenFromPaymentInstrumentEventDetails createPaymentDetailsTakenFromPaymentInstrumentEventDetails() {
        return new PaymentDetailsTakenFromPaymentInstrumentEventDetails(this);
    }
}
