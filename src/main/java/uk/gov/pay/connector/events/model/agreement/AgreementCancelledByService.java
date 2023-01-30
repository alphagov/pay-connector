package uk.gov.pay.connector.events.model.agreement;

import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.service.payments.commons.model.agreement.AgreementStatus;

import java.time.Instant;

public class AgreementCancelledByService extends AgreementEvent {

    public AgreementCancelledByService(String serviceId, boolean live, String resourceExternalId, Instant timestamp) {
        super(serviceId, live, resourceExternalId, timestamp);
    }

    public static AgreementCancelledByService from(AgreementEntity agreement, Instant timestamp) {
        return new AgreementCancelledByService(
                agreement.getServiceId(),
                agreement.getGatewayAccount().isLive(),
                agreement.getExternalId(),
                timestamp
        );
    }
}
