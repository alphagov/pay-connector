package uk.gov.pay.connector.payout;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.payout.PayoutCreated;
import uk.gov.pay.connector.events.model.payout.PayoutEvent;
import uk.gov.pay.connector.gateway.stripe.json.StripePayout;
import uk.gov.pay.connector.queue.QueueException;

import java.util.Optional;

public class PayoutEmitterService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final EventService eventService;
    private final Boolean shouldEmitPayoutEvents;

    @Inject
    public PayoutEmitterService(EventService eventService,
                                ConnectorConfiguration connectorConfiguration) {

        this.eventService = eventService;
        shouldEmitPayoutEvents = connectorConfiguration.getEmitPayoutEvents();
    }

    public void emitPayoutEvent(Class<? extends PayoutEvent> eventClass, String connectAccount, StripePayout payout) {
        Event event = getPayoutEvent(eventClass, connectAccount, payout);

        Optional.ofNullable(event)
                .ifPresent(this::sendToEventQueue);
    }

    private void sendToEventQueue(Event event) {
        try {
            if (shouldEmitPayoutEvents) {
                eventService.emitEvent(event, false);
            }
        } catch (QueueException e) {
            logger.error("Error sending payout event to event queue: event type [{}], payout id [{}] : exception [{}]",
                    event.getEventType(), event.getResourceExternalId(), e);
        }
    }

    private Event getPayoutEvent(Class eventClass, String connectAccount, StripePayout payout) {
        if (eventClass == PayoutCreated.class) {
            Event event = PayoutCreated.from(payout);
            return event;
        }
        logger.warn("Unsupported payout event class [{}] for payout [{}] ", eventClass,
                payout.getId());
        return null;
    }
}
