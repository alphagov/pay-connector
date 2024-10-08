package uk.gov.pay.connector.events.model.agreement;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import uk.gov.pay.connector.agreement.model.AgreementCancelRequest;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.service.payments.commons.api.json.IsoInstantMicrosecondSerializer;

import java.time.Instant;

public class AgreementCancelledByUser extends AgreementEvent {

    public AgreementCancelledByUser(String serviceId, boolean live, String resourceExternalId, EventDetails eventDetails, Instant timestamp) {
        super(serviceId, live, resourceExternalId, eventDetails, timestamp);
    }

    public static AgreementCancelledByUser from(AgreementEntity agreement, AgreementCancelRequest agreementCancelRequest, Instant timestamp) {
        return new AgreementCancelledByUser(
                agreement.getServiceId(),
                agreement.getGatewayAccount().isLive(),
                agreement.getExternalId(),
                new AgreementCancelledByUserEventDetails(agreementCancelRequest, timestamp),
                timestamp
        );
    }

    static class AgreementCancelledByUserEventDetails extends EventDetails {
        private final String userExternalId;
        private final String userEmail;
        @JsonSerialize(using = IsoInstantMicrosecondSerializer.class)
        private final Instant cancelledDate;

        public AgreementCancelledByUserEventDetails(AgreementCancelRequest agreementCancelRequest, Instant cancelledDate) {
            this.userExternalId = agreementCancelRequest.userExternalId();
            this.userEmail = agreementCancelRequest.userEmail();
            this.cancelledDate = cancelledDate;
        }

        public String getUserExternalId() {
            return userExternalId;
        }

        public String getUserEmail() {
            return userEmail;
        }

        public Instant getCancelledDate() {
            return cancelledDate;
        }
    }
}
