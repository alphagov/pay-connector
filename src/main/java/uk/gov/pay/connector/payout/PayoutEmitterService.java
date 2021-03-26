package uk.gov.pay.connector.payout;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.payout.PayoutCreated;
import uk.gov.pay.connector.events.model.payout.PayoutEvent;
import uk.gov.pay.connector.events.model.payout.PayoutFailed;
import uk.gov.pay.connector.events.model.payout.PayoutPaid;
import uk.gov.pay.connector.events.model.payout.PayoutUpdated;
import uk.gov.pay.connector.gateway.stripe.json.StripePayout;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.queue.QueueException;

import java.time.ZonedDateTime;
import java.util.Optional;

import static java.lang.String.format;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.gatewayaccount.model.StripeCredentials.STRIPE_ACCOUNT_ID_KEY;
import static uk.gov.service.payments.logging.LoggingKeys.CONNECT_ACCOUNT_ID;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_PAYOUT_ID;
import static uk.gov.service.payments.logging.LoggingKeys.LEDGER_EVENT_TYPE;

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

    public void emitPayoutEvent(Class<? extends PayoutEvent> eventClass, ZonedDateTime eventDate,
                                String connectAccount, StripePayout payout) {
        Event event = getPayoutEvent(eventClass, connectAccount, eventDate, payout);

        Optional.ofNullable(event)
                .ifPresent(payoutEvent -> sendToEventQueue(payoutEvent, connectAccount));
    }

    private void sendToEventQueue(Event event, String connectAccount) {
        try {
            if (shouldEmitPayoutEvents) {
                eventService.emitEvent(event, false);
                logger.info("Payout event sent to event queue",
                        kv(LEDGER_EVENT_TYPE, event.getEventType()),
                        kv(GATEWAY_PAYOUT_ID, event.getResourceExternalId()),
                        kv(CONNECT_ACCOUNT_ID, connectAccount));
            }
        } catch (QueueException e) {
            logger.error(format("Error sending payout event to event queue: exception [%s]", e.getMessage()),
                    kv(LEDGER_EVENT_TYPE, event.getEventType()),
                    kv(GATEWAY_PAYOUT_ID, event.getResourceExternalId()),
                    kv(CONNECT_ACCOUNT_ID, connectAccount));
        }
    }

    private Event getPayoutEvent(Class<? extends PayoutEvent> eventClass, String connectAccount,
                                 ZonedDateTime eventDate, StripePayout payout) {
        if (eventClass == PayoutCreated.class) {
            return getPayoutCreatedEvent(connectAccount, payout);
        } else if (eventClass == PayoutPaid.class) {
            return PayoutPaid.from(eventDate, payout);
        } else if (eventClass == PayoutFailed.class) {
            return PayoutFailed.from(eventDate, payout);
        } else if (eventClass == PayoutUpdated.class) {
            return PayoutUpdated.from(eventDate, payout);
        }

        logger.warn(format("Unsupported payout event class [%s]", eventClass),
                kv(GATEWAY_PAYOUT_ID, payout.getId()),
                kv(CONNECT_ACCOUNT_ID, connectAccount));
        return null;
    }

    private Event getPayoutCreatedEvent(String connectAccount, StripePayout payout) {
        Optional<GatewayAccountEntity> mayBeGatewayAccount = gatewayAccountDao.findByCredentialsKeyValue(
                STRIPE_ACCOUNT_ID_KEY, connectAccount);

        if (mayBeGatewayAccount.isPresent()) {
            return PayoutCreated.from(mayBeGatewayAccount.get().getId(), payout);
        } else {
            logger.error(format("Gateway account with Stripe connect account not found: connect_account_id [%s] ", connectAccount),
                    kv(GATEWAY_PAYOUT_ID, payout.getId()),
                    kv(CONNECT_ACCOUNT_ID, connectAccount));
            throw new GatewayAccountNotFoundException(
                    format("Gateway account with Stripe connect account ID %s not found.", connectAccount));

        }
    }
}
