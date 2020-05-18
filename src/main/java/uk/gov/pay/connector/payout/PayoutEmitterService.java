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
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.queue.QueueException;

import java.util.Optional;

import static java.lang.String.format;
import static uk.gov.pay.connector.gatewayaccount.model.StripeCredentials.STRIPE_ACCOUNT_ID_KEY;

public class PayoutEmitterService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final EventService eventService;
    private final boolean shouldEmitPayoutEvents;
    private final GatewayAccountDao gatewayAccountDao;

    @Inject
    public PayoutEmitterService(EventService eventService,
                                ConnectorConfiguration connectorConfiguration,
                                GatewayAccountDao gatewayAccountDao) {

        this.eventService = eventService;
        shouldEmitPayoutEvents = connectorConfiguration.getEmitPayoutEvents();
        this.gatewayAccountDao = gatewayAccountDao;
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
                    event.getEventType(), event.getResourceExternalId(), e.getMessage(), e);
        }
    }

    private Event getPayoutEvent(Class<? extends PayoutEvent> eventClass, String connectAccount, StripePayout payout) {
        if (eventClass == PayoutCreated.class) {
            Optional<GatewayAccountEntity> mayBeGatewayAccount = gatewayAccountDao.findByCredentialsKeyValue(
                    STRIPE_ACCOUNT_ID_KEY, connectAccount);

            if (mayBeGatewayAccount.isPresent()) {
                return PayoutCreated.from(mayBeGatewayAccount.get().getId(), payout);
            } else {
                logger.error("Gateway account with Stripe connect account ID {} not found", connectAccount);
                throw new GatewayAccountNotFoundException(
                        format("Gateway account with Stripe connect account ID %s not found.", connectAccount));

            }
        }
        logger.warn("Unsupported payout event class [{}] for payout [{}] ", eventClass,
                payout.getId());
        return null;
    }
}
