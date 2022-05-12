package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public class PaymentInstrumentConfirmed extends PaymentInstrumentEvent {

    public PaymentInstrumentConfirmed(String serviceId, boolean live, String resourceExternalId, EventDetails eventDetails, ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, eventDetails, timestamp);
    }

    public static PaymentInstrumentConfirmed from(AgreementEntity agreement) {
        return new PaymentInstrumentConfirmed(
                agreement.getServiceId(),
                agreement.isLive(),
                agreement.getPaymentInstrument().getExternalId(),
                new PaymentInstrumentConfirmedDetails(agreement),
                ZonedDateTime.now(ZoneOffset.UTC)
        );
    }

    static class PaymentInstrumentConfirmedDetails extends EventDetails {
        private String agreementExternalId;

        public PaymentInstrumentConfirmedDetails(AgreementEntity agreement) {
            this.agreementExternalId = agreement.getExternalId();
        }

        public String getAgreementExternalId() {
            return agreementExternalId;
        }
    }
}
