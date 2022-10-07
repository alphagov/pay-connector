package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;

import java.time.Instant;
import java.util.Objects;

public class PaymentInstrumentConfirmed extends PaymentInstrumentEvent {

    public PaymentInstrumentConfirmed(String serviceId, boolean live, String resourceExternalId, EventDetails eventDetails, Instant timestamp) {
        super(serviceId, live, resourceExternalId, eventDetails, timestamp);
    }

    public static PaymentInstrumentConfirmed from(AgreementEntity agreement, Instant timestamp) {
        String paymentInstrumentExternalId = agreement.getPaymentInstrument()
                .map(PaymentInstrumentEntity::getExternalId)
                .orElseThrow(() -> new IllegalArgumentException("Agreement " + agreement.getExternalId() + " does not have a payment instrument"));

        return new PaymentInstrumentConfirmed(
                agreement.getServiceId(),
                agreement.isLive(),
                paymentInstrumentExternalId,
                new PaymentInstrumentConfirmedDetails(agreement),
                timestamp
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PaymentInstrumentConfirmedDetails that = (PaymentInstrumentConfirmedDetails) o;
            return Objects.equals(agreementExternalId, that.agreementExternalId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(agreementExternalId);
        }
    }
}
