package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;

import java.time.ZonedDateTime;

public class AgreementCancelledByService extends AgreementEvent {

    public AgreementCancelledByService(String serviceId, boolean live, String resourceExternalId, EventDetails eventDetails, ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, eventDetails, timestamp);
    }

    public static AgreementCancelledByService from(AgreementEntity agreement, ZonedDateTime timestamp) {
        return new AgreementCancelledByService(
                agreement.getServiceId(),
                agreement.getGatewayAccount().isLive(),
                agreement.getExternalId(),
                new AgreementCancelledByServiceEventDetails(PaymentInstrumentStatus.CANCELLED),
                timestamp
        );
    }

    static class AgreementCancelledByServiceEventDetails extends EventDetails {
        private final String status;

        public AgreementCancelledByServiceEventDetails(PaymentInstrumentStatus status) {
            this.status = String.valueOf(status);
        }

        public String getStatus() {
            return status;
        }
    }
}
