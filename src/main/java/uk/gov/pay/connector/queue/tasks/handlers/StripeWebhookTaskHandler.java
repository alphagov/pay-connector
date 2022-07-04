package uk.gov.pay.connector.queue.tasks.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.eventdetails.dispute.DisputeCreatedEventDetails;
import uk.gov.pay.connector.events.eventdetails.dispute.DisputeEvidenceSubmittedEventDetails;
import uk.gov.pay.connector.events.eventdetails.dispute.DisputeLostEventDetails;
import uk.gov.pay.connector.events.eventdetails.dispute.DisputeWonEventDetails;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.dispute.DisputeCreated;
import uk.gov.pay.connector.events.model.dispute.DisputeEvent;
import uk.gov.pay.connector.events.model.dispute.DisputeEvidenceSubmitted;
import uk.gov.pay.connector.events.model.dispute.DisputeLost;
import uk.gov.pay.connector.events.model.dispute.DisputeWon;
import uk.gov.pay.connector.gateway.stripe.StripeNotificationStatus;
import uk.gov.pay.connector.gateway.stripe.response.StripeNotification;
import uk.gov.pay.connector.queue.tasks.dispute.StripeDisputeData;

import javax.inject.Inject;
import java.util.Optional;

import static java.lang.String.format;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.DISPUTE_CLOSED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.DISPUTE_CREATED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.DISPUTE_UPDATED;
import static uk.gov.pay.connector.util.RandomIdGenerator.idFromExternalId;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_DISPUTE_ID;
import static uk.gov.service.payments.logging.LoggingKeys.LEDGER_EVENT_TYPE;

public class StripeWebhookTaskHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(StripeWebhookTaskHandler.class);
    private final LedgerService ledgerService;
    private final EventService eventService;
    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    public StripeWebhookTaskHandler(LedgerService ledgerService, EventService eventService) {
        this.ledgerService = ledgerService;
        this.eventService = eventService;
    }

    public void process(StripeNotification stripeNotification) throws JsonProcessingException {
        if (stripeNotification.getType().equals(DISPUTE_CREATED.getType())) {
            var stripeDisputeData = mapper.readValue(stripeNotification.getObject(), StripeDisputeData.class);
            if (stripeDisputeData.getBalanceTransactionList().size() > 1) {
                throw new RuntimeException("Dispute data has too many balance_transactions");
            }
            Optional<LedgerTransaction> ledgerTransaction = ledgerService
                    .getTransactionForProviderAndGatewayTransactionId(STRIPE.getName(), stripeDisputeData.getPaymentIntentId());
            ledgerTransaction.map(transaction -> {
                var disputeCreatedEvent = createDisputeCreatedEvent(stripeDisputeData, transaction);

                emitEvent(disputeCreatedEvent);

                return disputeCreatedEvent;
            }).orElseThrow(() ->
                    new RuntimeException(format("LedgerTransaction with gateway transaction id [%s] not found",
                            stripeDisputeData.getPaymentIntentId())));
        } else if (stripeNotification.getType().equals(DISPUTE_CLOSED.getType())) {
            var stripeDisputeData = mapper.readValue(stripeNotification.getObject(), StripeDisputeData.class);
            if (stripeDisputeData.getBalanceTransactionList().size() > 1) {
                throw new RuntimeException("Dispute data has too many balance_transactions");
            }
            Optional<LedgerTransaction> ledgerTransaction = ledgerService
                    .getTransactionForProviderAndGatewayTransactionId(STRIPE.getName(), stripeDisputeData.getPaymentIntentId());
            ledgerTransaction.map(transaction -> {
                DisputeEvent disputeEvent;
                if (stripeDisputeData.getStatus().equalsIgnoreCase(StripeNotificationStatus.WON.name())) {
                    disputeEvent = createDisputeWonEvent(stripeNotification, stripeDisputeData, transaction);
                } else if (stripeDisputeData.getStatus().equalsIgnoreCase(StripeNotificationStatus.LOST.name())) {
                    disputeEvent = createDisputeLostEvent(stripeNotification, stripeDisputeData, transaction);
                } else {
                    logger.info("Unknown stripe dispute closed status: [status: {}, payment_intent: {}]", stripeDisputeData.getStatus(), stripeDisputeData.getPaymentIntentId());
                    throw new RuntimeException(format("Unknown stripe dispute closed status: [status: %s, payment_intent: %s]", stripeDisputeData.getStatus(), stripeDisputeData.getPaymentIntentId()));
                }
                if (disputeEvent != null) {
                    emitEvent(disputeEvent);
                }
                return disputeEvent;
            }).orElseThrow(() ->
                    new RuntimeException(format("LedgerTransaction with gateway transaction id [%s] not found",
                            stripeDisputeData.getPaymentIntentId())));
        } else if (stripeNotification.getType().equals(DISPUTE_UPDATED.getType())) {
            var stripeDisputeData = mapper.readValue(stripeNotification.getObject(), StripeDisputeData.class);
            if (stripeDisputeData.getStatus().equalsIgnoreCase(StripeNotificationStatus.UNDER_REVIEW.name())) {
                Optional<LedgerTransaction> ledgerTransaction = ledgerService
                        .getTransactionForProviderAndGatewayTransactionId(STRIPE.getName(), stripeDisputeData.getPaymentIntentId());
                ledgerTransaction.map(transaction -> {
                    var disputeUpdatedEvent = createDisputeEvidenceSubmittedEvent(stripeNotification, stripeDisputeData, transaction);

                    emitEvent(disputeUpdatedEvent);

                    return disputeUpdatedEvent;
                }).orElseThrow(() ->
                        new RuntimeException(format("LedgerTransaction with gateway transaction id [%s] not found",
                                stripeDisputeData.getPaymentIntentId())));
            } else {
                logger.info("Skipping dispute updated notification: [status: {}, payment_intent: {}]", stripeDisputeData.getStatus(), stripeDisputeData.getPaymentIntentId());
            }
        } else {
            throw new RuntimeException("Unknown webhook task: " + stripeNotification.getType());
        }
    }

    private void emitEvent(Event event) {
        eventService.emitEvent(event);
        logger.info("Event sent to payment event queue: {}", event.getResourceExternalId(),
                kv(LEDGER_EVENT_TYPE, event.getEventType()),
                kv(GATEWAY_DISPUTE_ID, event.getResourceExternalId()));
    }

    private DisputeCreated createDisputeCreatedEvent(StripeDisputeData stripeDisputeData,
                                                     LedgerTransaction transaction) {

        var balanceTransaction = stripeDisputeData.getBalanceTransactionList().get(0);
        var eventDetails = new DisputeCreatedEventDetails(Math.abs(balanceTransaction.getFee()),
                stripeDisputeData.getEvidenceDetails().getDueByTimestamp(), transaction.getGatewayAccountId(),
                Math.abs(balanceTransaction.getAmount()), balanceTransaction.getNetAmount(),
                stripeDisputeData.getReason());
        return new DisputeCreated(stripeDisputeData.getResourceExternalId(), transaction.getTransactionId(),
                transaction.getServiceId(), transaction.getLive(), eventDetails, stripeDisputeData.getDisputeCreated());
    }

    private DisputeWon createDisputeWonEvent(StripeNotification stripeNotification,
                                             StripeDisputeData stripeDisputeData,
                                             LedgerTransaction transaction) {
        var eventDetails = new DisputeWonEventDetails(transaction.getGatewayAccountId());
        return new DisputeWon(idFromExternalId(stripeDisputeData.getResourceExternalId()),
                transaction.getTransactionId(), transaction.getServiceId(), transaction.getLive(), eventDetails,
                stripeNotification.getCreated());
    }

    private DisputeLost createDisputeLostEvent(StripeNotification stripeNotification,
                                               StripeDisputeData stripeDisputeData,
                                             LedgerTransaction transaction) {
        var balanceTransaction = stripeDisputeData.getBalanceTransactionList().get(0);
        var eventDetails = new DisputeLostEventDetails(transaction.getGatewayAccountId(), balanceTransaction.getNetAmount(),
                Math.abs(stripeDisputeData.getAmount()), Math.abs(balanceTransaction.getFee()));
        return new DisputeLost(idFromExternalId(stripeDisputeData.getResourceExternalId()),
                transaction.getTransactionId(), transaction.getServiceId(), transaction.getLive(), eventDetails,
                stripeNotification.getCreated());
    }

    private DisputeEvidenceSubmitted createDisputeEvidenceSubmittedEvent(StripeNotification stripeNotification,
                                                                         StripeDisputeData stripeDisputeData,
                                                                         LedgerTransaction transaction) {
        var eventDetails = new DisputeEvidenceSubmittedEventDetails(transaction.getGatewayAccountId());
        return new DisputeEvidenceSubmitted(idFromExternalId(stripeDisputeData.getResourceExternalId()),
                transaction.getTransactionId(), transaction.getServiceId(), transaction.getLive(), eventDetails,
                stripeNotification.getCreated());
    }
}
