package uk.gov.pay.connector.util;

import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.time.Instant;
import java.util.Objects;
import java.util.stream.Stream;

public class AddPaymentInstrumentParams {

    private final Long paymentInstrumentId;
    private final String externalPaymentInstrumentId;
    private final Instant createdDate;
    private final Instant startDate;
    private final PaymentInstrumentStatus paymentInstrumentStatus;
    private final CardType cardType;
    private final String cardBrand;
    private final CardExpiryDate expiryDate;
    private final LastDigitsCardNumber lastDigitsCardNumber;
    private final String cardholderName;
    private final String addressLine1;
    private final String addressLine2;
    private final String city;
    private final String stateOrProvince;
    private final String postcode;
    private final String countryCode;

    public Long getPaymentInstrumentId() {
        return paymentInstrumentId;
    }

    public String getExternalPaymentInstrumentId() {
        return externalPaymentInstrumentId;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public PaymentInstrumentStatus getPaymentInstrumentStatus() {
        return paymentInstrumentStatus;
    }

    public CardType getCardType() {
        return cardType;
    }

    public String getCardBrand() {
        return cardBrand;
    }

    public CardExpiryDate getExpiryDate() {
        return expiryDate;
    }

    public LastDigitsCardNumber getLastDigitsCardNumber() {
        return lastDigitsCardNumber;
    }

    public String getCardholderName() {
        return cardholderName;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getCity() {
        return city;
    }

    public String getStateOrProvince() {
        return stateOrProvince;
    }

    public String getPostcode() {
        return postcode;
    }

    public String getCountryCode() {
        return countryCode;
    }

    private AddPaymentInstrumentParams(AddPaymentInstrumentParamsBuilder builder) {
        paymentInstrumentId = builder.paymentInstrumentId;
        externalPaymentInstrumentId = builder.externalPaymentInstrumentId;
        createdDate = builder.createdDate;
        startDate = builder.startDate;
        paymentInstrumentStatus = builder.paymentInstrumentStatus;
        cardType = builder.cardType;
        cardBrand = builder.cardBrand;
        expiryDate = builder.expiryDate;
        lastDigitsCardNumber = builder.lastDigitsCardNumber;
        cardholderName = builder.cardholderName;
        addressLine1 = builder.addressLine1;
        addressLine2 = builder.addressLine2;
        city = builder.city;
        stateOrProvince = builder.stateOrProvince;
        postcode = builder.postcode;
        countryCode = builder.countryCode;
    }

    public static final class AddPaymentInstrumentParamsBuilder {
        private Long paymentInstrumentId;
        private String externalPaymentInstrumentId = "anExternalPaymentInstrumentId";
        private Instant createdDate = Instant.now();
        private Instant startDate = Instant.now();
        private PaymentInstrumentStatus paymentInstrumentStatus = PaymentInstrumentStatus.ACTIVE;
        private CardType cardType = CardType.DEBIT;
        private String cardBrand = "visa";
        private CardExpiryDate expiryDate = CardExpiryDate.valueOf("12/27");
        private LastDigitsCardNumber lastDigitsCardNumber = LastDigitsCardNumber.of("1234");
        private String cardholderName = "Dr. Payment";
        private String addressLine1 = "1 Money Street";
        private String addressLine2 = "Payville";
        private String city = "Paytown";
        private String stateOrProvince;
        private String postcode = "PAY ME";
        private String countryCode = "GB";

        private AddPaymentInstrumentParamsBuilder() {
        }

        public static AddPaymentInstrumentParamsBuilder anAddPaymentInstrumentParams() {
            return new AddPaymentInstrumentParamsBuilder();
        }

        public AddPaymentInstrumentParamsBuilder withPaymentInstrumentId(Long paymentInstrumentId) {
            this.paymentInstrumentId = paymentInstrumentId;
            return this;
        }

        public AddPaymentInstrumentParamsBuilder withExternalPaymentInstrumentId(String externalPaymentInstrumentId) {
            this.externalPaymentInstrumentId = externalPaymentInstrumentId;
            return this;
        }

        public AddPaymentInstrumentParamsBuilder withCreatedDate(Instant createdDate) {
            this.createdDate = createdDate;
            return this;
        }

        public AddPaymentInstrumentParamsBuilder withStartDate(Instant startDate) {
            this.startDate = startDate;
            return this;
        }

        public AddPaymentInstrumentParamsBuilder withPaymentInstrumentStatus(PaymentInstrumentStatus paymentInstrumentStatus) {
            this.paymentInstrumentStatus = paymentInstrumentStatus;
            return this;
        }

        public AddPaymentInstrumentParamsBuilder withCardType(CardType cardType) {
            this.cardType = cardType;
            return this;
        }

        public AddPaymentInstrumentParamsBuilder withCardBrand(String cardBrand) {
            this.cardBrand = cardBrand;
            return this;
        }

        public AddPaymentInstrumentParamsBuilder withExpiryDate(CardExpiryDate expiryDate) {
            this.expiryDate = expiryDate;
            return this;
        }

        public AddPaymentInstrumentParamsBuilder withLastDigitsCardNumber(LastDigitsCardNumber lastDigitsCardNumber) {
            this.lastDigitsCardNumber = lastDigitsCardNumber;
            return this;
        }

        public AddPaymentInstrumentParamsBuilder withCardholderName(String cardholderName) {
            this.cardholderName = cardholderName;
            return this;
        }

        public AddPaymentInstrumentParamsBuilder withAddressLine1(String addressLine1) {
            this.addressLine1 = addressLine1;
            return this;
        }

        public AddPaymentInstrumentParamsBuilder withAddressLine2(String addressLine2) {
            this.addressLine2 = addressLine2;
            return this;
        }

        public AddPaymentInstrumentParamsBuilder withCity(String city) {
            this.city = city;
            return this;
        }

        public AddPaymentInstrumentParamsBuilder withStateOrProvince(String stateOrProvince) {
            this.stateOrProvince = stateOrProvince;
            return this;
        }

        public AddPaymentInstrumentParamsBuilder withPostcode(String postcode) {
            this.postcode = postcode;
            return this;
        }

        public AddPaymentInstrumentParamsBuilder withCountryCode(String countryCode) {
            this.countryCode = countryCode;
            return this;
        }

        public AddPaymentInstrumentParams build() {
            Stream.of(paymentInstrumentId, externalPaymentInstrumentId, createdDate, paymentInstrumentStatus).forEach(Objects::requireNonNull);
            return new AddPaymentInstrumentParams(this);
        }

    }

}
