package uk.gov.pay.connector.events.model.agreement;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.service.payments.commons.api.json.IsoInstantMicrosecondSerializer;

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
                new AgreementCancelledByServiceEventDetails(timestamp),
                timestamp
        );
    }

    static class AgreementCancelledByServiceEventDetails extends EventDetails {
        @JsonSerialize(using = IsoInstantMicrosecondSerializer.class)
        private final Instant cancelledDate;

        public AgreementCancelledByServiceEventDetails(Instant cancelledDate) {
            this.cancelledDate = cancelledDate;
        }

        public Instant getCancelledDate() {
            return cancelledDate;
        }
    }
}
