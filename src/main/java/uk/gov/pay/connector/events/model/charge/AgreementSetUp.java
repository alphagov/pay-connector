package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.service.payments.commons.model.agreement.AgreementStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class AgreementSetUp extends AgreementEvent {

    public AgreementSetUp(String serviceId, boolean live, String resourceExternalId, EventDetails eventDetails, Instant timestamp) {
        super(serviceId, live, resourceExternalId, eventDetails, timestamp);
    }

    public static AgreementSetUp from(AgreementEntity agreement, Instant timestamp) {
        return new AgreementSetUp(
                agreement.getServiceId(),
                agreement.getGatewayAccount().isLive(),
                agreement.getExternalId(),
                new AgreementSetUpEventDetails(agreement.getPaymentInstrument().orElse(null), AgreementStatus.ACTIVE),
                timestamp
        );
    }

    static class AgreementSetUpEventDetails extends EventDetails {
        private String paymentInstrumentExternalId;
        private AgreementStatus status;

        public AgreementSetUpEventDetails(PaymentInstrumentEntity paymentInstrumentEntity, AgreementStatus status) {
            this.paymentInstrumentExternalId = Optional.ofNullable(paymentInstrumentEntity)
                    .map(PaymentInstrumentEntity::getExternalId)
                    .orElse(null);
            this.status = status;
        }

        public String getPaymentInstrumentExternalId() {
            return paymentInstrumentExternalId;
        }

        public AgreementStatus getStatus() {
            return status;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AgreementSetUpEventDetails that = (AgreementSetUpEventDetails) o;
            return Objects.equals(paymentInstrumentExternalId, that.paymentInstrumentExternalId) && Objects.equals(status, that.status);
        }

        @Override
        public int hashCode() {
            return Objects.hash(paymentInstrumentExternalId, status);
        }
    }
}
