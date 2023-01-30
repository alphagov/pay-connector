package uk.gov.pay.connector.events.model.agreement;

import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.service.payments.commons.model.agreement.AgreementStatus;

import java.time.Instant;

public class AgreementCancelledByService extends AgreementEvent {

    public AgreementCancelledByService(String serviceId, boolean live, String resourceExternalId, EventDetails eventDetails, Instant timestamp) {
        super(serviceId, live, resourceExternalId, eventDetails, timestamp);
    }

    public static AgreementCancelledByService from(AgreementEntity agreement, Instant timestamp) {
        return new AgreementCancelledByService(
                agreement.getServiceId(),
                agreement.getGatewayAccount().isLive(),
                agreement.getExternalId(),
                new AgreementCancelledByServiceEventDetails(AgreementStatus.CANCELLED),
                timestamp
        );
    }

    static class AgreementCancelledByServiceEventDetails extends EventDetails {
        private final AgreementStatus status;

        public AgreementCancelledByServiceEventDetails(AgreementStatus status) {
            this.status = status;
        }

        public AgreementStatus getStatus() {
            return status;
        }
    }
}
