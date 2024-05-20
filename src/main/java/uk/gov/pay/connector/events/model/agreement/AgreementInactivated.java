package uk.gov.pay.connector.events.model.agreement;

import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;

import java.time.Instant;
import java.util.Optional;

public class AgreementInactivated extends AgreementEvent {
    public AgreementInactivated(String serviceId, boolean live, String resourceExternalId, EventDetails eventDetails, Instant timestamp) {
        super(serviceId, live, resourceExternalId, eventDetails, timestamp);
    }

    public static AgreementInactivated from(AgreementEntity agreement, String rejectedReason, Instant timestamp) {
        return new AgreementInactivated(
                agreement.getServiceId(),
                agreement.getGatewayAccount().isLive(),
                agreement.getExternalId(),
                new AgreementInactivated.AgreementInactivatedEventDetails(agreement.getPaymentInstrument().orElse(null), rejectedReason),
                timestamp
        );
    }

    public static class AgreementInactivatedEventDetails extends EventDetails {
        private String paymentInstrumentExternalId;
        private String reason;

        public AgreementInactivatedEventDetails(PaymentInstrumentEntity paymentInstrumentEntity, String rejectedReason) {
            this.paymentInstrumentExternalId = Optional.ofNullable(paymentInstrumentEntity)
                    .map(PaymentInstrumentEntity::getExternalId)
                    .orElse(null);
            this.reason = rejectedReason;
        }

        public String getPaymentInstrumentExternalId() {
            return paymentInstrumentExternalId;
        }

        public String getReason() {
            return reason;
        }
    }
}
