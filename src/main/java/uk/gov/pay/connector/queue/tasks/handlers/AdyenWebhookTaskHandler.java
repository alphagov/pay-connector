package uk.gov.pay.connector.queue.tasks.handlers;

import com.adyen.model.notification.NotificationRequest;
import com.adyen.model.notification.NotificationRequestItem;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.charge.PaymentDisputed;
import uk.gov.pay.connector.events.model.charge.RefundAvailabilityUpdated;
import uk.gov.pay.connector.events.model.dispute.DisputeCreated;
import uk.gov.pay.connector.events.model.dispute.DisputeEvidenceSubmitted;
import uk.gov.pay.connector.events.model.dispute.DisputeEvent;
import uk.gov.pay.connector.events.model.dispute.DisputeLost;
import uk.gov.pay.connector.events.model.dispute.DisputeWon;
import uk.gov.pay.connector.gateway.adyen.webhook.AdyenNotificationService;
import uk.gov.pay.connector.gateway.adyen.webhook.AdyenPaymentEvent;
import uk.gov.pay.connector.gateway.processor.ChargeNotificationProcessor;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;

import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability.EXTERNAL_AVAILABLE;
import static uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability.EXTERNAL_UNAVAILABLE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gateway.adyen.webhook.AdyenPaymentEvent.CHARGEBACK;
import static uk.gov.pay.connector.gateway.adyen.webhook.AdyenPaymentEvent.CHARGEBACK_REVERSED;
import static uk.gov.pay.connector.gateway.adyen.webhook.AdyenPaymentEvent.INFORMATION_SUPPLIED;
import static uk.gov.pay.connector.gateway.adyen.webhook.AdyenPaymentEvent.NOTIFICATION_OF_CHARGEBACK;
import static uk.gov.pay.connector.gateway.adyen.webhook.AdyenPaymentEvent.PREARBITRATION_LOST;
import static uk.gov.pay.connector.gateway.adyen.webhook.AdyenPaymentEvent.PREARBITRATION_WON;
import static uk.gov.service.payments.logging.LoggingKeys.DISPUTE_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_DISPUTE_ID;
import static uk.gov.service.payments.logging.LoggingKeys.LEDGER_EVENT_TYPE;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;
import static uk.gov.pay.connector.util.RandomIdGenerator.idFromExternalId;

public class AdyenWebhookTaskHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenWebhookTaskHandler.class);
    private final ChargeService chargeService;
    private final ChargeNotificationProcessor chargeNotificationProcessor;
    private final GatewayAccountService gatewayAccountService;
    private final AdyenNotificationService adyenNotificationService;
    private final LedgerService ledgerService;
    private final EventService eventService;
    private final InstantSource instantSource;

    @Inject
    public AdyenWebhookTaskHandler(ChargeService chargeService,
                                   ChargeNotificationProcessor chargeNotificationProcessor,
                                   GatewayAccountService gatewayAccountService,
                                   AdyenNotificationService adyenNotificationService,
                                   LedgerService ledgerService,
                                   EventService eventService,
                                   InstantSource instantSource) {
        this.chargeService = chargeService;
        this.chargeNotificationProcessor = chargeNotificationProcessor;
        this.gatewayAccountService = gatewayAccountService;
        this.adyenNotificationService = adyenNotificationService;
        this.ledgerService = ledgerService;
        this.eventService = eventService;
        this.instantSource = instantSource;
    }

    @Transactional
    public void processAdyenWebhookNotification(String payload) {
        NotificationRequest notificationRequest =
                adyenNotificationService.deserialisePayloadToNotificationRequest(payload);

        List<NotificationRequestItem> items = adyenNotificationService.extractNotificationItem(notificationRequest);

        for (NotificationRequestItem item : items) {
            String eventCode = item.getEventCode();
            
            if (isDisputeEvent(eventCode)) {
                processDisputeNotification(item);
            } else {
                processCapturedNotification(item);
            }
        }
    }

    private boolean isDisputeEvent(String eventCode) {
        return eventCode.equals(NOTIFICATION_OF_CHARGEBACK.name()) ||
                eventCode.equals(CHARGEBACK.name()) ||
                eventCode.equals(CHARGEBACK_REVERSED.name()) ||
                eventCode.equals(INFORMATION_SUPPLIED.name()) ||
                eventCode.equals(PREARBITRATION_WON.name()) ||
                eventCode.equals(PREARBITRATION_LOST.name());
    }

    private void processDisputeNotification(NotificationRequestItem item) {
        String eventCode = item.getEventCode();
        String gatewayTransactionId = item.getOriginalReference();
        String disputePspReference = item.getPspReference();

        Optional<LedgerTransaction> mayBeLedgerTransaction = ledgerService
                .getTransactionForProviderAndGatewayTransactionId(ADYEN.getName(), gatewayTransactionId);

        if (mayBeLedgerTransaction.isEmpty()) {
            LOGGER.warn("LedgerTransaction not found for Adyen dispute webhook",
                    kv("gatewayTransactionId", gatewayTransactionId),
                    kv("eventCode", eventCode),
                    kv("disputePspReference", disputePspReference));
            return;
        }

        LedgerTransaction transaction = mayBeLedgerTransaction.get();
        String disputeExternalId = idFromExternalId(disputePspReference);

        try {
            MDC.put(DISPUTE_EXTERNAL_ID, disputeExternalId);
            MDC.put(GATEWAY_DISPUTE_ID, disputePspReference);
            MDC.put(PAYMENT_EXTERNAL_ID, transaction.getTransactionId());

            Instant eventTimestamp = item.getEventDate().toInstant();

            switch (AdyenPaymentEvent.valueOf(eventCode)) {
                case NOTIFICATION_OF_CHARGEBACK:
                    handleDisputeCreated(transaction, disputeExternalId, disputePspReference, eventTimestamp);
                    break;
                case INFORMATION_SUPPLIED:
                    handleDisputeEvidenceSubmitted(transaction, disputeExternalId, eventTimestamp);
                    break;
                case CHARGEBACK:
                    handleDisputeLost(transaction, disputeExternalId, disputePspReference, eventTimestamp);
                    break;
                case CHARGEBACK_REVERSED:
                case PREARBITRATION_WON:
                    handleDisputeWon(transaction, disputeExternalId, eventTimestamp);
                    break;
                case PREARBITRATION_LOST:
                    handleDisputeLost(transaction, disputeExternalId, disputePspReference, eventTimestamp);
                    break;
                default:
                    LOGGER.info("Unhandled Adyen dispute event",
                            kv("eventCode", eventCode),
                            kv("disputePspReference", disputePspReference));
            }
        } finally {
            List.of(DISPUTE_EXTERNAL_ID, GATEWAY_DISPUTE_ID, PAYMENT_EXTERNAL_ID).forEach(MDC::remove);
        }
    }

    private void handleDisputeCreated(LedgerTransaction transaction, String disputeExternalId, 
                                      String disputePspReference, Instant eventTimestamp) {
        // Similar to Stripe's DISPUTE_CREATED
        // Note: Adyen doesn't provide evidence due date in the webhook, so we use null
        DisputeCreated disputeCreatedEvent = new DisputeCreated(
                disputeExternalId,
                transaction.getTransactionId(),
                transaction.getServiceId(),
                transaction.getLive(),
                new uk.gov.pay.connector.events.eventdetails.dispute.DisputeCreatedEventDetails(
                        null, // evidenceDueBy - not provided by Adyen webhook
                        transaction.getGatewayAccountId(),
                        transaction.getAmount(), // amount - not provided by Adyen webhook
                        "Adyen chargeback", // reason - not provided by Adyen webhook
                        disputePspReference),
                eventTimestamp);
        emitEvent(disputeCreatedEvent);

        PaymentDisputed paymentDisputedEvent = PaymentDisputed.from(transaction, eventTimestamp);
        emitEvent(paymentDisputedEvent);

        RefundAvailabilityUpdated refundAvailabilityUpdated = RefundAvailabilityUpdated.from(
                transaction, EXTERNAL_UNAVAILABLE, eventTimestamp);
        emitEvent(refundAvailabilityUpdated);
    }

    private void handleDisputeEvidenceSubmitted(LedgerTransaction transaction, String disputeExternalId, 
                                                Instant eventTimestamp) {
        // Similar to Stripe's DISPUTE_UPDATED with UNDER_REVIEW status
        DisputeEvidenceSubmitted disputeEvidenceSubmittedEvent = DisputeEvidenceSubmitted.from(
                disputeExternalId, eventTimestamp, transaction);
        emitEvent(disputeEvidenceSubmittedEvent);
    }

    private void handleDisputeLost(LedgerTransaction transaction, String disputeExternalId, 
                                   String disputePspReference, Instant eventTimestamp) {
        // Similar to Stripe's DISPUTE_CLOSED with LOST status
        // Note: Adyen doesn't provide fee details in the webhook, so we use 0
        DisputeLost disputeLostEvent = new DisputeLost(
                disputeExternalId,
                transaction.getTransactionId(),
                transaction.getServiceId(),
                transaction.getLive(),
                new uk.gov.pay.connector.events.eventdetails.dispute.DisputeLostEventDetails(
                        transaction.getGatewayAccountId(),
                        transaction.getAmount()),
                eventTimestamp);
        emitEvent(disputeLostEvent);
    }

    private void handleDisputeWon(LedgerTransaction transaction, String disputeExternalId, 
                                  Instant eventTimestamp) {
        // Similar to Stripe's DISPUTE_CLOSED with WON status
        DisputeWon disputeWonEvent = DisputeWon.from(disputeExternalId, eventTimestamp, transaction);
        emitEvent(disputeWonEvent);

        RefundAvailabilityUpdated refundAvailabilityUpdatedEvent = RefundAvailabilityUpdated.from(
                transaction, EXTERNAL_AVAILABLE, eventTimestamp);
        emitEvent(refundAvailabilityUpdatedEvent);
    }

    private void emitEvent(Event event) {
        eventService.emitEvent(event);
        LOGGER.info("Event sent to payment event queue: {}", event.getResourceExternalId(),
                kv(LEDGER_EVENT_TYPE, event.getEventType()));
    }

    private void processCapturedNotification(NotificationRequestItem item) {
        String gatewayTransactionId = item.getOriginalReference();

        Optional<Charge> charge = chargeService.findByProviderAndTransactionIdFromDbOrLedger(
                ADYEN.getName(), gatewayTransactionId);

        if (charge.isPresent()) {
            Charge foundCharge = charge.get();
            ChargeStatus targetStatus = item.isSuccess() ? CAPTURED : CAPTURE_ERROR;

            if (foundCharge.isHistoric()) {
                Optional<GatewayAccountEntity> gatewayAccount = gatewayAccountService.getGatewayAccount(
                        foundCharge.getGatewayAccountId());

                gatewayAccount.ifPresentOrElse(gatewayAccountEntity ->
                                chargeNotificationProcessor.processCaptureNotificationForExpungedCharge(gatewayAccountEntity,
                                        gatewayTransactionId, foundCharge, targetStatus),
                        () -> LOGGER.error("GatewayAccount not found for foundCharge",
                                kv(PAYMENT_EXTERNAL_ID, foundCharge.getExternalId())));
            } else {
                chargeNotificationProcessor.invoke(gatewayTransactionId, foundCharge, targetStatus, ZonedDateTime.ofInstant(
                        item.getEventDate().toInstant(), ZoneId.of("UTC")));
            }

            if (!item.isSuccess()) {
                LOGGER.error("Capture failed",
                        kv("gateway_transaction_id", gatewayTransactionId),
                        kv("eventCode", item.getEventCode()));
            }

        } else {
            LOGGER.warn("Charge not found in Connector or Ledger for Adyen capture webhook",
                    kv("gatewayTransactionId", gatewayTransactionId));
        }

    }

}
