package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.charge.exception.ChargeEventNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.charge.BackFillerGatewayTransactionIdSetEventDetails;

import java.time.Instant;
import java.time.ZonedDateTime;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;

public class BackfillerGatewayTransactionIdSet extends PaymentEvent {

    public BackfillerGatewayTransactionIdSet(String serviceId,
                                             boolean live,
                                             Long gatewayAccountId,
                                             String resourceExternalId,
                                             BackFillerGatewayTransactionIdSetEventDetails eventDetails,
                                             Instant timestamp) {
        super(serviceId, live, gatewayAccountId, resourceExternalId, eventDetails, timestamp);
    }

    public static BackfillerGatewayTransactionIdSet from(ChargeEntity charge) {
        ZonedDateTime lastEventDate = charge.getEvents().stream()
                .filter(e -> e.getStatus() == ENTERING_CARD_DETAILS)
                .map(ChargeEventEntity::getUpdated)
                .max(ZonedDateTime::compareTo)
                .orElseThrow(() -> new ChargeEventNotFoundRuntimeException(charge.getExternalId()));

        return new BackfillerGatewayTransactionIdSet(
                charge.getServiceId(),
                charge.getGatewayAccount().isLive(),
                charge.getGatewayAccount().getId(),
                charge.getExternalId(),
                BackFillerGatewayTransactionIdSetEventDetails.from(charge),
                lastEventDate.toInstant());
    }
}
