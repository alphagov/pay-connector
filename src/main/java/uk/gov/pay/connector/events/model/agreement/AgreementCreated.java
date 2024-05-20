package uk.gov.pay.connector.events.model.agreement;

import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.time.Instant;

public class AgreementCreated extends AgreementEvent {

    public AgreementCreated(String serviceId, boolean live, String resourceExternalId, EventDetails eventDetails, Instant timestamp) {
        super(serviceId, live, resourceExternalId, eventDetails, timestamp);
    }

    public static AgreementCreated from(AgreementEntity agreement) {
        return new AgreementCreated(
                agreement.getServiceId(),
                agreement.getGatewayAccount().isLive(),
                agreement.getExternalId(),
                new AgreementCreatedEventDetails(
                        agreement.getGatewayAccount().getId(),
                        agreement.getReference(),
                        agreement.getDescription(),
                        agreement.getUserIdentifier()
                ),
                agreement.getCreatedDate()
        );
    }

    static class AgreementCreatedEventDetails extends EventDetails {
        private final String gatewayAccountId;
        private final String reference;
        private final String description;
        private final String userIdentifier;

        public AgreementCreatedEventDetails(Long gatewayAccountId, String reference, String description, String userIdentifier) {
            this.gatewayAccountId = String.valueOf(gatewayAccountId);
            this.reference = reference;
            this.description = description;
            this.userIdentifier = userIdentifier;
        }

        public String getGatewayAccountId() {
            return gatewayAccountId;
        }

        public String getReference() {
            return reference;
        }

        public String getDescription() {
            return description;
        }

        public String getUserIdentifier() {
            return userIdentifier;
        }
    }
}
