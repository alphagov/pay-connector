package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public class AgreementCreated extends AgreementEvent {

    public AgreementCreated(String serviceId, boolean live, String resourceExternalId, EventDetails eventDetails, ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, eventDetails, timestamp);
    }

    public static AgreementCreated from(AgreementEntity agreement) {
        return new AgreementCreated(
                agreement.getServiceId(),
                agreement.getGatewayAccount().isLive(),
                agreement.getExternalId(),
                new AgreementCreatedEventDetails(String.valueOf(agreement.getGatewayAccount().getId()), agreement.getReference(), "CREATED"),
                ZonedDateTime.ofInstant(agreement.getCreatedDate(), ZoneOffset.UTC)
        );
    }

    // service id, external id, live and created date all go with the event 
    static class AgreementCreatedEventDetails extends EventDetails {
        private String gatewayAccountId;
        private String reference;
        private String status;

        public AgreementCreatedEventDetails(String gatewayAccountId, String reference, String status) {
            this.gatewayAccountId = gatewayAccountId;
            this.reference = reference;
            this.status = status;
        }

        public String getStatus() {
            return status;
        }

        public String getGatewayAccountId() {
            return gatewayAccountId;
        }

        public String getReference() {
            return reference;
        }
    }
}
