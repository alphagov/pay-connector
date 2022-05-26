package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

public class AgreementSetup extends AgreementEvent {

    public AgreementSetup(String serviceId, boolean live, String resourceExternalId, EventDetails eventDetails, ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, eventDetails, timestamp);
    }

    public static AgreementSetup from(AgreementEntity agreement, ZonedDateTime timestamp) {
        return new AgreementSetup(
                agreement.getServiceId(),
                agreement.getGatewayAccount().isLive(),
                agreement.getExternalId(),
                new AgreementSetupEventDetails(agreement.getPaymentInstrument(), PaymentInstrumentStatus.ACTIVE),
                timestamp
        );
    }

    static class AgreementSetupEventDetails extends EventDetails {
        private String paymentInstrumentExternalId;
        private String status;

        public AgreementSetupEventDetails(PaymentInstrumentEntity paymentInstrumentEntity, PaymentInstrumentStatus status) {
            this.paymentInstrumentExternalId = Optional.ofNullable(paymentInstrumentEntity)
                    .map(PaymentInstrumentEntity::getExternalId)
                    .orElse(null);
            this.status = String.valueOf(status);
        }

        public String getPaymentInstrumentExternalId() {
            return paymentInstrumentExternalId;
        }

        public String getStatus() {
            return status;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AgreementSetupEventDetails that = (AgreementSetupEventDetails) o;
            return Objects.equals(paymentInstrumentExternalId, that.paymentInstrumentExternalId) && Objects.equals(status, that.status);
        }

        @Override
        public int hashCode() {
            return Objects.hash(paymentInstrumentExternalId, status);
        }
    }
}
