package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.charge.PaymentCreatedEventDetails;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public class PaymentCreated extends PaymentEvent {

    public PaymentCreated(String serviceId, boolean isLive, String resourceExternalId, PaymentCreatedEventDetails eventDetails, ZonedDateTime timestamp) {
        super(serviceId, isLive, resourceExternalId, eventDetails, timestamp);
    }

    public static PaymentCreated from(ChargeEventEntity event) {
        return new PaymentCreated(
                event.getChargeEntity().getServiceId(),
                event.getChargeEntity().getGatewayAccount().isLive(),
                event.getChargeEntity().getExternalId(),
                PaymentCreatedEventDetails.from(event.getChargeEntity()),
                event.getUpdated()
        );
    }

    public static PaymentCreated from(ChargeEntity charge) {
        return new PaymentCreated(
                charge.getServiceId(),
                charge.getGatewayAccount().isLive(), 
                charge.getExternalId(),
                PaymentCreatedEventDetails.from(charge),
                ZonedDateTime.ofInstant(charge.getCreatedDate(), ZoneOffset.UTC)
        );
    }
}
