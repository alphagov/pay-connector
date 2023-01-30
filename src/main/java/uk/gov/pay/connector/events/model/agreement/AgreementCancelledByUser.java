package uk.gov.pay.connector.events.model.agreement;

import uk.gov.pay.connector.agreement.model.AgreementCancelRequest;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.service.payments.commons.model.agreement.AgreementStatus;

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
                new AgreementCancelledByUserEventDetails(agreementCancelRequest, AgreementStatus.CANCELLED),
                timestamp
        );
    }

    static class AgreementCancelledByUserEventDetails extends EventDetails {
        private final String userExternalId;
        private final String userEmail;
        private final AgreementStatus status;

        public AgreementCancelledByUserEventDetails(AgreementCancelRequest agreementCancelRequest, AgreementStatus status) {
            this.userExternalId = agreementCancelRequest.getUserExternalId();
            this.userEmail = agreementCancelRequest.getUserEmail();
            this.status = status;
        }

        public String getUserExternalId() {
            return userExternalId;
        }

        public String getUserEmail() {
            return userEmail;
        }

        public AgreementStatus getStatus() {
            return status;
        }
    }
}
