package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

public class AgreementSetup extends AgreementEvent {

    public AgreementSetup(String serviceId, boolean live, String resourceExternalId, EventDetails eventDetails, ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, eventDetails, timestamp);
    }

    public static AgreementSetup from(AgreementEntity agreement) {
        return new AgreementSetup(
                agreement.getServiceId(),
                agreement.getGatewayAccount().isLive(),
                agreement.getExternalId(),
                new AgreementSetupEventDetails(Optional.ofNullable(agreement.getPaymentInstrument().getExternalId()).orElse(null), "ACTIVE"),
                ZonedDateTime.now(ZoneOffset.UTC)
        );
    }

    // service id, external id, live and created date all go with the event 
    static class AgreementSetupEventDetails extends EventDetails {
        private String paymentInstrumentExternalId;
        private String status;

        public AgreementSetupEventDetails(String paymentExternalId, String status) {
            this.paymentInstrumentExternalId = paymentExternalId;
            this.status = status;
        }

        public String getPaymentInstrumentExternalId() {
            return paymentInstrumentExternalId;
        }

        public String getStatus() {
            return status;
        }
    }
}
