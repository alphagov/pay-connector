package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.service.payments.commons.model.CardExpiryDate;
import uk.gov.service.payments.commons.model.agreement.PaymentInstrumentType;

import java.time.Instant;
import java.util.Optional;

public class PaymentInstrumentCreated extends PaymentInstrumentEvent {

    public PaymentInstrumentCreated(String serviceId, boolean live, String resourceExternalId, EventDetails eventDetails, Instant timestamp) {
        super(serviceId, live, resourceExternalId, eventDetails, timestamp);
    }

    public static PaymentInstrumentCreated from(PaymentInstrumentEntity paymentInstrument, GatewayAccountEntity gatewayAccount) {
        return new PaymentInstrumentCreated(
                gatewayAccount.getServiceId(),
                gatewayAccount.isLive(),
                paymentInstrument.getExternalId(),
                PaymentInstrumentCreatedDetails.from(paymentInstrument, PaymentInstrumentType.CARD),
                paymentInstrument.getCreatedDate()
        );
    }

    static class PaymentInstrumentCreatedDetails extends EventDetails {

        private final String cardholderName;
        private final String addressLine1;
        private final String addressLine2;
        private final String addressPostcode;
        private final String addressCity;
        private final String addressCounty;
        private final String addressCountry;
        private final String lastDigitsCardNumber;
        private final String firstDigitsCardNumber;
        private final String expiryDate;
        private final String cardBrand;
        private final String cardType;
        private final PaymentInstrumentType type;

        private PaymentInstrumentCreatedDetails(PaymentInstrumentCreatedDetailsBuilder builder) {
            this.cardholderName = builder.cardholderName;
            this.addressLine1 = builder.addressLine1;
            this.addressLine2 = builder.addressLine2;
            this.addressPostcode = builder.addressPostcode;
            this.addressCity = builder.addressCity;
            this.addressCounty = builder.addressCounty;
            this.addressCountry = builder.addressCountry;
            this.lastDigitsCardNumber = builder.lastDigitsCardNumber;
            this.firstDigitsCardNumber = builder.firstDigitsCardNumber;
            this.expiryDate = builder.expiryDate;
            this.cardBrand = builder.cardBrand;
            this.cardType = builder.cardType;
            this.type = builder.type;
        }

        public static PaymentInstrumentCreatedDetails from(PaymentInstrumentEntity paymentInstrument, PaymentInstrumentType type) {
            PaymentInstrumentCreatedDetailsBuilder builder = new PaymentInstrumentCreatedDetailsBuilder(type);

            Optional.ofNullable(paymentInstrument.getCardDetails())
                    .ifPresent(cardDetails -> {
                        builder.withCardholderName(cardDetails.getCardHolderName());
                        Optional.ofNullable(cardDetails.getLastDigitsCardNumber()).map(LastDigitsCardNumber::toString).ifPresent(builder::withLastDigitsCardNumber);
                        Optional.ofNullable(cardDetails.getFirstDigitsCardNumber()).map(FirstDigitsCardNumber::toString).ifPresent(builder::withFirstDigitsCardNumber);
                        Optional.ofNullable(cardDetails.getExpiryDate()).map(CardExpiryDate::toString).ifPresent(builder::withExpiryDate);
                        builder.withCardBrand(cardDetails.getCardBrand());
                        Optional.ofNullable(cardDetails.getCardType()).map(CardType::name).ifPresent(builder::withCardType);

                        cardDetails.getBillingAddress().ifPresent(billingAddress -> {
                            builder.withAddressLine1(billingAddress.getLine1());
                            builder.withAddressLine2(billingAddress.getLine2());
                            builder.withAddressPostcode(billingAddress.getPostcode());
                            builder.withAddressCity(billingAddress.getCity());
                            builder.withAddressCounty(billingAddress.getCounty());
                            builder.withAddressCountry(billingAddress.getCountry());        
                        });
                    });

            return builder.build();
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

        public String getAddressPostcode() {
            return addressPostcode;
        }

        public String getAddressCity() {
            return addressCity;
        }

        public String getAddressCounty() {
            return addressCounty;
        }

        public String getAddressCountry() {
            return addressCountry;
        }

        public String getLastDigitsCardNumber() {
            return lastDigitsCardNumber;
        }

        public String getExpiryDate() {
            return expiryDate;
        }

        public String getCardBrand() {
            return cardBrand;
        }

        public PaymentInstrumentType getType() {
            return type;
        }

        public String getFirstDigitsCardNumber() {
            return firstDigitsCardNumber;
        }

        public String getCardType() {
            return cardType;
        }

        private static class PaymentInstrumentCreatedDetailsBuilder {

            private final PaymentInstrumentType type;
            private String cardholderName;
            private String addressLine1;
            private String addressLine2;
            private String addressPostcode;
            private String addressCity;
            private String addressCounty;
            private String addressCountry;
            private String lastDigitsCardNumber;
            private String firstDigitsCardNumber;
            private String expiryDate;
            private String cardBrand;
            private String cardType;
            
            public PaymentInstrumentCreatedDetailsBuilder(PaymentInstrumentType paymentInstrumentType) {
                this.type = paymentInstrumentType;
            }
            
            public PaymentInstrumentCreatedDetailsBuilder withCardholderName(String cardHolderName) {
                this.cardholderName = cardHolderName;
                return this;
            }

            public PaymentInstrumentCreatedDetailsBuilder withAddressLine1(String addressLine1) {
                this.addressLine1 = addressLine1;
                return this;
            }

            public PaymentInstrumentCreatedDetailsBuilder withAddressLine2(String addressLine2) {
                this.addressLine2 = addressLine2;
                return this;
            }

            public PaymentInstrumentCreatedDetailsBuilder withAddressPostcode(String addressPostcode) {
                this.addressPostcode = addressPostcode;
                return this;
            }

            public PaymentInstrumentCreatedDetailsBuilder withAddressCity(String addressCity) {
                this.addressCity = addressCity;
                return this;
            }

            public PaymentInstrumentCreatedDetailsBuilder withAddressCounty(String addressCounty) {
                this.addressCounty = addressCounty;
                return this;
            }

            public PaymentInstrumentCreatedDetailsBuilder withAddressCountry(String addressCountry) {
                this.addressCountry = addressCountry;
                return this;
            }

            public PaymentInstrumentCreatedDetailsBuilder withLastDigitsCardNumber(String lastDigitsCardNumber) {
                this.lastDigitsCardNumber = lastDigitsCardNumber;
                return this;
            }

            public PaymentInstrumentCreatedDetailsBuilder withFirstDigitsCardNumber(String firstDigitsCardNumber) {
                this.firstDigitsCardNumber = firstDigitsCardNumber;
                return this;
            }

            public PaymentInstrumentCreatedDetailsBuilder withExpiryDate(String expiryDate) {
                this.expiryDate = expiryDate;
                return this;
            }

            public PaymentInstrumentCreatedDetailsBuilder withCardBrand(String cardBrand) {
                this.cardBrand = cardBrand;
                return this;
            }
            
            public PaymentInstrumentCreatedDetailsBuilder withCardType(String cardType) {
                this.cardType = cardType;
                return this;
            }

            public PaymentInstrumentCreatedDetails build() {
                return new PaymentInstrumentCreatedDetails(this);
            }

        }

    }

}
