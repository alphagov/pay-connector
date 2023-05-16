package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.charge.PaymentCreatedEventDetails;

import java.time.Instant;

public class PaymentCreated extends PaymentEvent {

    public PaymentCreated(String serviceId, boolean live, Long gatewayAccountInternalId, String resourceExternalId, PaymentCreatedEventDetails eventDetails, Instant timestamp) {
        super(serviceId, live, gatewayAccountInternalId, resourceExternalId, eventDetails, timestamp);
    }

    public static PaymentCreated from(ChargeEventEntity event) {
        return new PaymentCreated(
                event.getChargeEntity().getServiceId(),
                event.getChargeEntity().getGatewayAccount().isLive(),
                event.getChargeEntity().getGatewayAccount().getId(),
                event.getChargeEntity().getExternalId(),
                PaymentCreatedEventDetails.from(event.getChargeEntity()),
                event.getUpdated().toInstant()
        );
    }

    public static PaymentCreated from(ChargeEntity charge) {
        return new PaymentCreated(
                charge.getServiceId(),
                charge.getGatewayAccount().isLive(),
                charge.getGatewayAccount().getId(),
                charge.getExternalId(),
                PaymentCreatedEventDetails.from(charge),
                charge.getCreatedDate()
        );
    }
}
