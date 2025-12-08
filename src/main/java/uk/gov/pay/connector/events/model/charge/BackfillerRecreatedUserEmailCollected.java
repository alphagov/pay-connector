package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.charge.exception.ChargeEventNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.charge.UserEmailCollectedEventDetails;

import java.time.Instant;
import java.time.ZonedDateTime;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;

/**
 * `UserEmailCollected` event is emitted with connector instance timestamp (as event date) when email is updated for
 * a charge (using PATCH endpoint) and there is no associated charge event.
 * This (`BackfillerRecreatedUserEmailCollected`) event is equivalent to `UserEmailCollected` event but used during
 * backfilling charge and uses timestamp of CREATED/ENTERING_CARD_DETAILS event as event date.
 */
public class BackfillerRecreatedUserEmailCollected extends PaymentEvent {

    public BackfillerRecreatedUserEmailCollected(String serviceId,
                                                 boolean live,
                                                 Long gatewayAccountId,
                                                 String resourceExternalId,
                                                 UserEmailCollectedEventDetails eventDetails,
                                                 Instant timestamp) {
        super(serviceId, live, gatewayAccountId, resourceExternalId, eventDetails, timestamp);
    }

    public static BackfillerRecreatedUserEmailCollected from(ChargeEntity charge) {
        ZonedDateTime lastEventDate = charge.getEvents().stream()
                .filter(e -> e.getStatus() == ENTERING_CARD_DETAILS || e.getStatus() == CREATED)
                .map(ChargeEventEntity::getUpdated)
                .max(ZonedDateTime::compareTo)
                .orElseThrow(() -> new ChargeEventNotFoundRuntimeException(charge.getExternalId()));

        return new BackfillerRecreatedUserEmailCollected(
                charge.getServiceId(),
                charge.getGatewayAccount().isLive(),
                charge.getGatewayAccount().getId(),
                charge.getExternalId(),
                UserEmailCollectedEventDetails.from(charge),
                lastEventDate.toInstant());
    }
}
