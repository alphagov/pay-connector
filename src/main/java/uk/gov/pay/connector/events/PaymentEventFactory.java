package uk.gov.pay.connector.events;

import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;

import java.lang.reflect.InvocationTargetException;
import java.time.ZonedDateTime;

public class PaymentEventFactory {

    public static PaymentEvent create(Class<? extends PaymentEvent> eventClass, ChargeEventEntity chargeEvent) {
        try {
            if (eventClass == PaymentCreated.class) {
                return PaymentCreated.from(chargeEvent);
            } else if (eventClass == PaymentDetailsEvent.class) {
                return PaymentDetailsEvent.from(chargeEvent);
            } else {
                return eventClass.getConstructor(String.class, ZonedDateTime.class).newInstance(
                        chargeEvent.getChargeEntity().getExternalId(),
                        chargeEvent.getUpdated()
                );
            }
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalArgumentException(String.format("Could not construct payment event: %s", eventClass));
        }
    }
}
