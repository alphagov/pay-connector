package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.agreement.model.AgreementCancelRequest;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public class AgreementCancelledByUser extends AgreementEvent {

    public AgreementCancelledByUser(String serviceId, boolean live, String resourceExternalId, EventDetails eventDetails, ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, eventDetails, timestamp);
    }

    public static AgreementCancelledByUser from(AgreementEntity agreement, AgreementCancelRequest agreementCancelRequest, ZonedDateTime timestamp) {
        return new AgreementCancelledByUser(
                agreement.getServiceId(),
                agreement.getGatewayAccount().isLive(),
                agreement.getExternalId(),
                new AgreementCancelledByUserEventDetails(agreementCancelRequest, PaymentInstrumentStatus.CANCELLED),
                timestamp
        );
    }

    static class AgreementCancelledByUserEventDetails extends EventDetails {
        private final String userExternalId;
        private final String userEmail;
        private final String status;

        public AgreementCancelledByUserEventDetails(AgreementCancelRequest agreementCancelRequest, PaymentInstrumentStatus status) {
            this.userExternalId = agreementCancelRequest.getUserExternalId();
            this.userEmail = agreementCancelRequest.getUserEmail();
            this.status = String.valueOf(status);
        }

        public String getUserExternalId() {
            return userExternalId;
        }

        public String getUserEmail() {
            return userEmail;
        }

        public String getStatus() {
            return status;
        }
    }
}
