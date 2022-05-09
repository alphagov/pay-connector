package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;

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
                new AgreementCreatedEventDetails(
                        agreement.getGatewayAccount().getId(),
                        agreement.getReference(),
                        PaymentInstrumentStatus.CREATED
                ),
                ZonedDateTime.ofInstant(agreement.getCreatedDate(), ZoneOffset.UTC)
        );
    }

    static class AgreementCreatedEventDetails extends EventDetails {
        private final String gatewayAccountId;
        private final String reference;
        private final String status;

        public AgreementCreatedEventDetails(Long gatewayAccountId, String reference, PaymentInstrumentStatus status) {
            this.gatewayAccountId = String.valueOf(gatewayAccountId);
            this.reference = reference;
            this.status = String.valueOf(status);
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
