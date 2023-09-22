package uk.gov.pay.connector.queue.tasks.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.charge.PaymentDisputed;
import uk.gov.pay.connector.events.model.charge.RefundAvailabilityUpdated;
import uk.gov.pay.connector.events.model.dispute.DisputeCreated;
import uk.gov.pay.connector.events.model.dispute.DisputeEvent;
import uk.gov.pay.connector.events.model.dispute.DisputeEvidenceSubmitted;
import uk.gov.pay.connector.events.model.dispute.DisputeLost;
import uk.gov.pay.connector.events.model.dispute.DisputeWon;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.stripe.StripeDisputeStatus;
import uk.gov.pay.connector.gateway.stripe.StripeFullTestCardNumbers;
import uk.gov.pay.connector.gateway.stripe.StripeNotificationType;
import uk.gov.pay.connector.gateway.stripe.StripePaymentProvider;
import uk.gov.pay.connector.gateway.stripe.json.StripeDisputeData;
import uk.gov.pay.connector.gateway.stripe.response.StripeNotification;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountCredentialsNotFoundException;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;

import javax.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability.EXTERNAL_UNAVAILABLE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.stripe.StripeDisputeStatus.LOST;
import static uk.gov.pay.connector.gateway.stripe.StripeDisputeStatus.UNDER_REVIEW;
import static uk.gov.pay.connector.gateway.stripe.StripeDisputeStatus.WARNING_CLOSED;
import static uk.gov.pay.connector.gateway.stripe.StripeDisputeStatus.WARNING_NEEDS_RESPONSE;
import static uk.gov.pay.connector.gateway.stripe.StripeDisputeStatus.WARNING_UNDER_REVIEW;
import static uk.gov.pay.connector.gateway.stripe.StripeDisputeStatus.WON;
import static uk.gov.pay.connector.gateway.stripe.StripeDisputeStatus.byStatus;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.DISPUTE_CLOSED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.DISPUTE_CREATED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.DISPUTE_UPDATED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.byType;
import static uk.gov.pay.connector.util.RandomIdGenerator.idFromExternalId;
import static uk.gov.service.payments.logging.LoggingKeys.DISPUTE_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_DISPUTE_ID;
import static uk.gov.service.payments.logging.LoggingKeys.LEDGER_EVENT_TYPE;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;

public class StripeWebhookTaskHandler {

    private static final Logger logger = LoggerFactory.getLogger(StripeWebhookTaskHandler.class);
    private final LedgerService ledgerService;
    private final ChargeService chargeService;
    private final EventService eventService;
    private final StripePaymentProvider stripePaymentProvider;
    private final GatewayAccountService gatewayAccountService;
    private final GatewayAccountCredentialsService gatewayAccountCredentialsService;
    private final StripeGatewayConfig stripeGatewayConfig;
    private final Clock clock;

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<StripeNotificationType> disputeTypes = List.of(DISPUTE_CREATED, DISPUTE_UPDATED, DISPUTE_CLOSED);

    @Inject
    public StripeWebhookTaskHandler(LedgerService ledgerService,
                                    ChargeService chargeService, EventService eventService,
                                    StripePaymentProvider stripePaymentProvider,
                                    GatewayAccountService gatewayAccountService,
                                    GatewayAccountCredentialsService gatewayAccountCredentialsService,
                                    ConnectorConfiguration configuration, 
                                    Clock clock) {
        this.ledgerService = ledgerService;
        this.chargeService = chargeService;
        this.eventService = eventService;
        this.stripePaymentProvider = stripePaymentProvider;
        this.gatewayAccountService = gatewayAccountService;
        this.gatewayAccountCredentialsService = gatewayAccountCredentialsService;
        this.stripeGatewayConfig = configuration.getStripeConfig();
        this.clock = clock;
    }

    public void process(StripeNotification stripeNotification) throws JsonProcessingException, GatewayException {
        StripeNotificationType stripeNotificationType = byType(stripeNotification.getType());
        if (disputeTypes.contains(stripeNotificationType)) {
            try {
                StripeDisputeData stripeDisputeData = deserialiseStripeDisputeData(stripeNotification);
                LedgerTransaction transaction = getLedgerTransaction(stripeDisputeData);

                String disputeExternalId = idFromExternalId(stripeDisputeData.getId());
                MDC.put(DISPUTE_EXTERNAL_ID, disputeExternalId);
                MDC.put(GATEWAY_DISPUTE_ID, stripeDisputeData.getId());
                MDC.put(PAYMENT_EXTERNAL_ID, transaction.getTransactionId());

                boolean isTestStripeTransaction = Boolean.FALSE.equals(stripeDisputeData.getLiveMode());
                StripeDisputeStatus disputeStatus = byStatus(stripeDisputeData.getStatus());

                if (disputeStatus == WARNING_NEEDS_RESPONSE || disputeStatus == WARNING_UNDER_REVIEW || disputeStatus == WARNING_CLOSED) {
                    logger.warn("Skipping dispute notification: [status: {}, type: {}, payment_intent: {}, reason: {}]",
                            stripeDisputeData.getStatus(), stripeNotificationType, stripeDisputeData.getPaymentIntentId(),
                            stripeDisputeData.getReason());
                    return;
                }
                
                switch (stripeNotificationType) {
                    case DISPUTE_CREATED:
                        DisputeCreated disputeCreatedEvent = DisputeCreated.from(disputeExternalId, stripeDisputeData, transaction,
                                stripeDisputeData.getDisputeCreated().toInstant());
                        emitEvent(disputeCreatedEvent);
                        PaymentDisputed paymentDisputedEvent = PaymentDisputed.from(transaction, stripeDisputeData.getDisputeCreated().toInstant());
                        emitEvent(paymentDisputedEvent);
                        // NOTE: we update the refund availability in ledger - but for connector it is calculated separately.
                        // So this status update will block a refund attempt made VIA the API is made if the charge has been
                        // expunged from connector.
                        RefundAvailabilityUpdated refundAvailabilityUpdated = RefundAvailabilityUpdated.from(
                                transaction, EXTERNAL_UNAVAILABLE, clock.instant());
                        emitEvent(refundAvailabilityUpdated);

                        if (isTestStripeTransaction) {
                            submitEvidenceForTestAccount(stripeDisputeData, transaction);
                        }
                        break;
                    case DISPUTE_UPDATED:
                        if (disputeStatus == UNDER_REVIEW) {
                            DisputeEvidenceSubmitted disputeUpdatedEvent = DisputeEvidenceSubmitted.from(
                                    disputeExternalId, stripeNotification.getCreated().toInstant(), transaction);

                            emitEvent(disputeUpdatedEvent);
                        } else {
                            logger.info("Skipping dispute updated notification: [status: {}, payment_intent: {}]",
                                    stripeDisputeData.getStatus(), stripeDisputeData.getPaymentIntentId());
                        }
                        break;
                    case DISPUTE_CLOSED:
                        DisputeEvent disputeEvent;

                        // For test transactions, the dispute updated and dispute closed notification will have the same
                        // created date. Add a second onto the timestamp for events we send to ledger to ensure these
                        // appear in the correct order.
                        Instant disputeClosedEventTimestamp = isTestStripeTransaction
                                ? stripeNotification.getCreated().plus(1, ChronoUnit.SECONDS).toInstant()
                                : stripeNotification.getCreated().toInstant();

                        if (disputeStatus == WON) {
                            disputeEvent = DisputeWon.from(disputeExternalId, disputeClosedEventTimestamp, transaction);
                            Charge charge = Charge.from(transaction);
                            RefundAvailabilityUpdated refundAvailabilityUpdatedEvent = chargeService.createRefundAvailabilityUpdatedEvent(charge,
                                    disputeClosedEventTimestamp);
                            emitEvent(refundAvailabilityUpdatedEvent);
                        } else if (disputeStatus == LOST) {
                            disputeEvent = handleDisputeLost(stripeDisputeData, transaction, disputeExternalId, disputeClosedEventTimestamp);
                        } else {
                            logger.info("Unknown stripe dispute status: [status: {}, payment_intent: {}]",
                                    stripeDisputeData.getStatus(), stripeDisputeData.getPaymentIntentId());
                            throw new RuntimeException(format("Unknown stripe dispute status: [status: %s, payment_intent: %s]",
                                    stripeDisputeData.getStatus(), stripeDisputeData.getPaymentIntentId()));
                        }
                        emitEvent(disputeEvent);
                        break;
                    default:
                        logger.info("Skipping dispute updated notification: [status: {}, payment_intent: {}]",
                                stripeDisputeData.getStatus(), stripeDisputeData.getPaymentIntentId());
                }
            } finally {
                List.of(DISPUTE_EXTERNAL_ID, GATEWAY_DISPUTE_ID, PAYMENT_EXTERNAL_ID).forEach(MDC::remove);
            }
        } else {
            throw new RuntimeException("Unknown webhook task: " + stripeNotification.getType());
        }
    }

    private DisputeEvent handleDisputeLost(StripeDisputeData stripeDisputeData, LedgerTransaction transaction, 
                                           String disputeExternalId, Instant eventTimestamp) throws GatewayException {
        boolean rechargeDispute = shouldRechargeDispute(stripeDisputeData, transaction);
        if (rechargeDispute) {
            Charge charge = Charge.from(transaction);
            GatewayAccountEntity gatewayAccount = gatewayAccountService.getGatewayAccount(Long.valueOf(transaction.getGatewayAccountId()))
                    .orElseThrow(() -> new GatewayAccountNotFoundException(transaction.getGatewayAccountId()));
            GatewayAccountCredentialsEntity gatewayAccountCredentials = gatewayAccountCredentialsService.findCredentialFromCharge(charge, gatewayAccount)
                    .orElseThrow(() -> new GatewayAccountCredentialsNotFoundException("Unable to resolve gateway account credentials for charge " + charge.getExternalId()));
            stripePaymentProvider.transferDisputeAmount(stripeDisputeData, charge, gatewayAccount, gatewayAccountCredentials);
        } else {
            logger.info("Skipping recharging for dispute {} for payment {} as it was created before the date we started recharging from",
                    stripeDisputeData.getId(), transaction.getTransactionId());
        }
        return DisputeLost.from(disputeExternalId, stripeDisputeData, eventTimestamp, transaction, rechargeDispute);
    }

    private boolean shouldRechargeDispute(StripeDisputeData stripeDisputeData, LedgerTransaction transaction) {
        return (transaction.getLive() && stripeDisputeData.getDisputeCreated().toInstant().isAfter(stripeGatewayConfig.getRechargeServicesForLivePaymentDisputesFromDate())) ||
                (!transaction.getLive() && stripeDisputeData.getDisputeCreated().toInstant().isAfter(stripeGatewayConfig.getRechargeServicesForTestPaymentDisputesFromDate()));
    }

    private void submitEvidenceForTestAccount(StripeDisputeData stripeDisputeData, LedgerTransaction transaction) {
        if (transaction.getCardDetails() == null) {
            throw new RuntimeException("Card details are not yet available on ledger transaction to submit test evidence");
        }
        Optional<String> evidenceText =
                StripeFullTestCardNumbers.getSubmitTestDisputeEvidenceText(transaction.getCardDetails().getFirstDigitsCardNumber(),
                        transaction.getCardDetails().getLastDigitsCardNumber(), transaction.getCardDetails().getCardholderName());
        evidenceText.ifPresent(submitEvidenceText -> {
            try {
                StripeDisputeData dispute = stripePaymentProvider.submitTestDisputeEvidence(stripeDisputeData.getId(),
                        submitEvidenceText, transaction.getTransactionId());
                logger.info("Updated dispute [{}] with evidence [{}] for transaction [{}]", dispute.getId(),
                        dispute.getEvidence().getUncategorizedText(),
                        transaction.getTransactionId());
            } catch (GatewayException e) {
                logger.info("Failed to post evidence for Stripe test account: error [{}], dispute id [{}], transaction id [{}]",
                        e.getMessage(), stripeDisputeData.getId(), transaction.getTransactionId());
            }
        });
    }

    private LedgerTransaction getLedgerTransaction(StripeDisputeData stripeDisputeData) {
        Optional<LedgerTransaction> mayBeLedgerTransaction = ledgerService
                .getTransactionForProviderAndGatewayTransactionId(STRIPE.getName(), stripeDisputeData.getPaymentIntentId());

        return mayBeLedgerTransaction.orElseThrow(() ->
                new RuntimeException(format("LedgerTransaction with gateway transaction id [%s] not found",
                        stripeDisputeData.getPaymentIntentId())));
    }

    private StripeDisputeData deserialiseStripeDisputeData(StripeNotification stripeNotification) throws
            JsonProcessingException {
        return mapper.readValue(stripeNotification.getObject(), StripeDisputeData.class);
    }

    private void emitEvent(Event event) {
        eventService.emitEvent(event);
        logger.info("Event sent to payment event queue: {}", event.getResourceExternalId(),
                kv(LEDGER_EVENT_TYPE, event.getEventType()));
    }
}
