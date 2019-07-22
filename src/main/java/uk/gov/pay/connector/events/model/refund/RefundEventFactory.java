package uk.gov.pay.connector.events.model.refund;

import uk.gov.pay.connector.refund.model.domain.RefundHistory;

import java.lang.reflect.InvocationTargetException;
import java.time.ZonedDateTime;

public class RefundEventFactory {
    public static RefundEvent create(Class<? extends RefundEvent> eventClass, RefundHistory refundHistory) {
        try {
            if (eventClass == RefundCreatedByService.class) {
                return RefundCreatedByService.from(refundHistory);
            } else if (eventClass == RefundCreatedByUser.class) {
                return RefundCreatedByUser.from(refundHistory);
            } else {
                return eventClass.getConstructor(String.class, String.class, ZonedDateTime.class).newInstance(
                        refundHistory.getExternalId(),
                        refundHistory.getChargeEntity().getExternalId(),
                        refundHistory.getHistoryStartDate()
                );
            }
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalArgumentException(String.format("Could not construct refund event: %s", eventClass));
        }
    }
}
