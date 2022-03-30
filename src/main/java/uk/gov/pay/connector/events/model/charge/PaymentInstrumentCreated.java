package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

public class PaymentInstrumentCreated extends PaymentInstrumentEvent {

    public PaymentInstrumentCreated(String serviceId, boolean live, String resourceExternalId, EventDetails eventDetails, ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, eventDetails, timestamp);
    }

    public static PaymentInstrumentCreated from(PaymentInstrumentEntity paymentInstrument, GatewayAccountEntity gatewayAccount) {
        return new PaymentInstrumentCreated(
                gatewayAccount.getServiceId(),
                gatewayAccount.isLive(),
                paymentInstrument.getExternalId(),
                new PaymentInstrumentCreatedDetails(paymentInstrument),
                ZonedDateTime.ofInstant(paymentInstrument.getCreatedDate(), ZoneOffset.UTC)
        );
    }

    // service id, external id, live and created date all go with the event 
    static class PaymentInstrumentCreatedDetails extends EventDetails {
        private String cardholderName;
        private String addressLine1;
        private String addressLine2;
        private String addressPostcode;
        private String addressCity;
        private String addressCounty;
        private String addressCountry;
        private String lastDigitsCardNumber;
        private String expiryDate;
        private String cardBrand; 

        public PaymentInstrumentCreatedDetails(PaymentInstrumentEntity paymentInstrument) {
            this.cardholderName = paymentInstrument.getCardDetails().getCardHolderName();
            this.lastDigitsCardNumber = paymentInstrument.getCardDetails().getLastDigitsCardNumber().toString();
            this.expiryDate = paymentInstrument.getCardDetails().getExpiryDate().toString();
            this.cardBrand =  paymentInstrument.getCardDetails().getCardBrand();
            
            paymentInstrument.getCardDetails().getBillingAddress().ifPresent(billingAddress -> {
                this.addressLine1 = billingAddress.getLine1();
                this.addressLine2 = billingAddress.getLine2();
                this.addressPostcode = billingAddress.getPostcode();
                this.addressCity = billingAddress.getCity();
                this.addressCounty = billingAddress.getCounty();
                this.addressCountry = billingAddress.getCountry();
            });
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
    }
}
