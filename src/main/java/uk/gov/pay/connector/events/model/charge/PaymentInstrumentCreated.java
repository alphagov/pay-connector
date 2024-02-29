package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.paymentprocessor.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.paymentprocessor.model.LastDigitsCardNumber;
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
                new PaymentInstrumentCreatedDetails(paymentInstrument, PaymentInstrumentType.CARD),
                paymentInstrument.getCreatedDate()
        );
    }

    static class PaymentInstrumentCreatedDetails extends EventDetails {
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
        private PaymentInstrumentType type;

        public PaymentInstrumentCreatedDetails(PaymentInstrumentEntity paymentInstrument, PaymentInstrumentType type) {
            Optional.ofNullable(paymentInstrument.getCardDetails())
                    .ifPresent(cardDetails -> {
                        this.cardholderName = cardDetails.getCardHolderName();
                        this.lastDigitsCardNumber = Optional.ofNullable(cardDetails.getLastDigitsCardNumber()).map(LastDigitsCardNumber::toString).orElse(null);
                        this.firstDigitsCardNumber = Optional.ofNullable(cardDetails.getFirstDigitsCardNumber()).map(FirstDigitsCardNumber::toString).orElse(null);
                        this.expiryDate = Optional.ofNullable(cardDetails.getExpiryDate()).map(CardExpiryDate::toString).orElse(null);
                        this.cardBrand = cardDetails.getCardBrand();
                        this.cardType = Optional.ofNullable(cardDetails.getCardType()).map(CardType::name).orElse(null);
                        
                        cardDetails.getBillingAddress().ifPresent(billingAddress -> {
                            this.addressLine1 = billingAddress.getLine1();
                            this.addressLine2 = billingAddress.getLine2();
                            this.addressPostcode = billingAddress.getPostcode();
                            this.addressCity = billingAddress.getCity();
                            this.addressCounty = billingAddress.getCounty();
                            this.addressCountry = billingAddress.getCountry();        
                        });
                    });
            this.type = type;
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
    }
}
