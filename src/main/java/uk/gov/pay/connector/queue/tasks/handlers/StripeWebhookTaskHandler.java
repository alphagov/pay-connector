package uk.gov.pay.connector.queue.tasks.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.dispute.DisputeCreated;
import uk.gov.pay.connector.events.model.dispute.DisputeEvent;
import uk.gov.pay.connector.events.model.dispute.DisputeEvidenceSubmitted;
import uk.gov.pay.connector.events.model.dispute.DisputeLost;
import uk.gov.pay.connector.events.model.dispute.DisputeWon;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.stripe.StripeFullTestCardNumbers;
import uk.gov.pay.connector.gateway.stripe.StripeDisputeStatus;
import uk.gov.pay.connector.gateway.stripe.StripeNotificationType;
import uk.gov.pay.connector.gateway.stripe.StripePaymentProvider;
import uk.gov.pay.connector.gateway.stripe.response.StripeNotification;
import uk.gov.pay.connector.gateway.stripe.response.StripeDisputeData;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountCredentialsNotFoundException;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.stripe.StripeDisputeStatus.LOST;
import static uk.gov.pay.connector.gateway.stripe.StripeDisputeStatus.UNDER_REVIEW;
import static uk.gov.pay.connector.gateway.stripe.StripeDisputeStatus.WON;
import static uk.gov.pay.connector.gateway.stripe.StripeDisputeStatus.byStatus;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.DISPUTE_CLOSED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.DISPUTE_CREATED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.DISPUTE_UPDATED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.byType;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_DISPUTE_ID;
import static uk.gov.service.payments.logging.LoggingKeys.LEDGER_EVENT_TYPE;

public class StripeWebhookTaskHandler {

    private static final Logger logger = LoggerFactory.getLogger(StripeWebhookTaskHandler.class);
    private final LedgerService ledgerService;
    private final EventService eventService;
    private final StripePaymentProvider stripePaymentProvider;
    private final GatewayAccountService gatewayAccountService;
    private final GatewayAccountCredentialsService gatewayAccountCredentialsService;
    private final StripeGatewayConfig stripeGatewayConfig;

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<StripeNotificationType> disputeTypes = List.of(DISPUTE_CREATED, DISPUTE_UPDATED, DISPUTE_CLOSED);

    @Inject
    public StripeWebhookTaskHandler(LedgerService ledgerService,
                                    EventService eventService,
                                    StripePaymentProvider stripePaymentProvider,
                                    GatewayAccountService gatewayAccountService,
                                    GatewayAccountCredentialsService gatewayAccountCredentialsService,
                                    ConnectorConfiguration configuration) {
        this.ledgerService = ledgerService;
        this.eventService = eventService;
        this.stripePaymentProvider = stripePaymentProvider;
        this.gatewayAccountService = gatewayAccountService;
        this.gatewayAccountCredentialsService = gatewayAccountCredentialsService;
        this.stripeGatewayConfig = configuration.getStripeConfig();
    }

    public void process(StripeNotification stripeNotification) throws JsonProcessingException, GatewayException {
        StripeNotificationType stripeNotificationType = byType(stripeNotification.getType());
        if (disputeTypes.contains(stripeNotificationType)) {
            StripeDisputeData stripeDisputeData = deserialiseStripeDisputeData(stripeNotification);
            LedgerTransaction transaction = getLedgerTransaction(stripeDisputeData);

            switch (stripeNotificationType) {
                case DISPUTE_CREATED:
                    DisputeCreated disputeCreatedEvent = DisputeCreated.from(stripeDisputeData, transaction, stripeDisputeData.getDisputeCreated());
                    emitEvent(disputeCreatedEvent, stripeDisputeData.getId());
                    if(!stripeDisputeData.getLiveMode()) {
                        submitEvidenceForTestAccount(stripeDisputeData, transaction);
                    }
                    break;
                case DISPUTE_UPDATED:
                    StripeDisputeStatus stripeDisputeStatus = byStatus(stripeDisputeData.getStatus());
                    if (stripeDisputeStatus == UNDER_REVIEW) {
                        DisputeEvidenceSubmitted disputeUpdatedEvent = DisputeEvidenceSubmitted.from(
                                stripeDisputeData.getId(), stripeNotification.getCreated(), transaction);

                        emitEvent(disputeUpdatedEvent, stripeDisputeData.getId());
                    } else {
                        logger.info("Skipping dispute updated notification: [status: {}, payment_intent: {}]",
                                stripeDisputeData.getStatus(), stripeDisputeData.getPaymentIntentId());
                    }
                    break;
                case DISPUTE_CLOSED:
                    StripeDisputeStatus disputeStatus = byStatus(stripeDisputeData.getStatus());
                    DisputeEvent disputeEvent;

                    if (disputeStatus == WON) {
                        disputeEvent = DisputeWon.from(stripeDisputeData.getId(), stripeNotification.getCreated(), transaction);
                    } else if (disputeStatus == LOST) {
                        disputeEvent = handleDisputeLost(stripeNotification, stripeDisputeData, transaction);
                    } else {
                        logger.info("Unknown stripe dispute status: [status: {}, payment_intent: {}]",
                                stripeDisputeData.getStatus(), stripeDisputeData.getPaymentIntentId());
                        throw new RuntimeException(format("Unknown stripe dispute status: [status: %s, payment_intent: %s]",
                                stripeDisputeData.getStatus(), stripeDisputeData.getPaymentIntentId()));
                    }
                    emitEvent(disputeEvent, stripeDisputeData.getId());
                    break;
                default:
                    logger.info("Skipping dispute updated notification: [status: {}, payment_intent: {}]",
                            stripeDisputeData.getStatus(), stripeDisputeData.getPaymentIntentId());
            }
        } else {
            throw new RuntimeException("Unknown webhook task: " + stripeNotification.getType());
        }
    }

    private DisputeEvent handleDisputeLost(StripeNotification stripeNotification, StripeDisputeData stripeDisputeData,
                                           LedgerTransaction transaction) throws GatewayException {
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
        return DisputeLost.from(stripeDisputeData, stripeNotification.getCreated(), transaction, rechargeDispute);
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
                        transaction.getCardDetails().getLastDigitsCardNumber());
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

    private void emitEvent(Event event, String gatewayDisputeId) {
        eventService.emitEvent(event);
        logger.info("Event sent to payment event queue: {}", event.getResourceExternalId(),
                kv(LEDGER_EVENT_TYPE, event.getEventType()),
                kv("dispute_external_id", event.getResourceExternalId()),
                kv(GATEWAY_DISPUTE_ID, gatewayDisputeId));
    }
}
