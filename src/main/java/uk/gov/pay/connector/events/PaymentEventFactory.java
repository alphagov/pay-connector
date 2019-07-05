package uk.gov.pay.connector.events;

import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;

import java.lang.reflect.InvocationTargetException;
import java.time.ZonedDateTime;

public class PaymentEventFactory {

    public static PaymentEvent create(Class<? extends PaymentEvent> eventClass, ChargeEventEntity chargeEventEntity) {
        try {
            if (eventClass == PaymentCreated.class) {
                return PaymentCreated.from(chargeEventEntity);
            } else {
                return eventClass.getConstructor(String.class, ZonedDateTime.class).newInstance(
                        chargeEventEntity.getChargeEntity().getExternalId(),
                        chargeEventEntity.getUpdated()
                );
            }
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalArgumentException(String.format("Could not construct payment event: %s", eventClass));
        }
    }
}
