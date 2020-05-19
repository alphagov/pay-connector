package uk.gov.pay.connector.payout;

import com.stripe.model.BalanceTransaction;
import com.stripe.model.Charge;
import com.stripe.model.Transfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.charge.PaymentIncludedInPayout;
import uk.gov.pay.connector.events.model.refund.RefundIncludedInPayout;
import uk.gov.pay.connector.gateway.stripe.request.StripeTransferMetadata;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.StripeCredentials;
import uk.gov.pay.connector.queue.QueueException;
import uk.gov.pay.connector.queue.payout.PayoutReconcileMessage;
import uk.gov.pay.connector.queue.payout.PayoutReconcileQueue;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.String.format;

public class PayoutReconcileProcess {

    private static final Logger LOGGER = LoggerFactory.getLogger(PayoutReconcileProcess.class);
    private PayoutReconcileQueue payoutReconcileQueue;
    private StripeClientWrapper stripeClientWrapper;
    private StripeGatewayConfig stripeGatewayConfig;
    private ConnectorConfiguration connectorConfiguration;
    private GatewayAccountDao gatewayAccountDao;
    private EventService eventService;

    @Inject
    public PayoutReconcileProcess(PayoutReconcileQueue payoutReconcileQueue,
                                  StripeClientWrapper stripeClientWrapper,
                                  StripeGatewayConfig stripeGatewayConfig,
                                  ConnectorConfiguration connectorConfiguration,
                                  GatewayAccountDao gatewayAccountDao,
                                  EventService eventService) {
        this.payoutReconcileQueue = payoutReconcileQueue;
        this.stripeClientWrapper = stripeClientWrapper;
        this.stripeGatewayConfig = stripeGatewayConfig;
        this.connectorConfiguration = connectorConfiguration;
        this.gatewayAccountDao = gatewayAccountDao;
        this.eventService = eventService;
    }

    public void processPayouts() throws QueueException {
        List<PayoutReconcileMessage> payoutReconcileMessages = payoutReconcileQueue.retrievePayoutMessages();
        for (PayoutReconcileMessage payoutReconcileMessage : payoutReconcileMessages) {
            try {
                LOGGER.info("Processing payout [{}] for connect account [{}]",
                        payoutReconcileMessage.getGatewayPayoutId(),
                        payoutReconcileMessage.getConnectAccountId());

                String apiKey = getStripeApiKey(payoutReconcileMessage.getConnectAccountId());

                AtomicInteger payments = new AtomicInteger();
                AtomicInteger refunds = new AtomicInteger();

                stripeClientWrapper.getBalanceTransactionsForPayout(payoutReconcileMessage.getGatewayPayoutId(), payoutReconcileMessage.getConnectAccountId(), apiKey)
                        .forEach(balanceTransaction -> {
                            switch (balanceTransaction.getType()) {
                                case "payment":
                                    emitPaymentEvent(payoutReconcileMessage, balanceTransaction);
                                    payments.getAndIncrement();
                                    break;
                                // Refunds have a balance transaction of type "transfer" as refunds are made from our 
                                // Platform Stripe account, and then a transfer is made for the amount from the connect
                                // account.
                                case "transfer":
                                    emitRefundEvent(payoutReconcileMessage, balanceTransaction);
                                    refunds.getAndIncrement();
                                    break;
                                // There is a balance transaction for the payout itself, ignore this.
                                case "payout":
                                    break;
                                default:
                                    LOGGER.error("Payout [{}] for connect account [{}] contains balance transfer of type [{}], which is unexpected.",
                                            payoutReconcileMessage.getGatewayPayoutId(),
                                            payoutReconcileMessage.getConnectAccountId(),
                                            balanceTransaction.getType());
                                    break;
                            }
                        });

                if (payments.intValue() == 0 && refunds.intValue() == 0) {
                    LOGGER.error("No payments or refunds retrieved for payout [{}] for connect account [{}]. Requires investigation.",
                            payoutReconcileMessage.getGatewayPayoutId(), payoutReconcileMessage.getConnectAccountId());
                } else {
                    LOGGER.info("Finished processing payout [{}] for connect account [{}]. Emitted events for {} payments and {} refunds.",
                            payoutReconcileMessage.getGatewayPayoutId(),
                            payoutReconcileMessage.getConnectAccountId(),
                            payments.intValue(),
                            refunds.intValue());

                    payoutReconcileQueue.markMessageAsProcessed(payoutReconcileMessage.getQueueMessage());
                }
            } catch (Exception e) {
                LOGGER.error("Error processing payout from SQS message [queueMessageId={}] [errorMessage={}]",
                        payoutReconcileMessage.getQueueMessageId(),
                        e.getMessage()
                );
            }
        }
    }

    private String getStripeApiKey(String stripeAccountId) {
        return gatewayAccountDao.findByCredentialsKeyValue(StripeCredentials.STRIPE_ACCOUNT_ID_KEY, stripeAccountId)
                .map(gatewayAccountEntity ->
                        gatewayAccountEntity.isLive() ? stripeGatewayConfig.getAuthTokens().getLive() : stripeGatewayConfig.getAuthTokens().getTest())
                .orElseThrow(() -> new RuntimeException(format("Gateway account with Stripe connect account ID [%s] not found.", stripeAccountId)));
    }

    private void emitPaymentEvent(PayoutReconcileMessage payoutReconcileMessage, BalanceTransaction balanceTransaction) {
        var paymentSource = (Charge) balanceTransaction.getSourceObject();
        var paymentSourceTransfer = paymentSource.getSourceTransferObject();
        String paymentExternalId = resolveTransactionExternalId(payoutReconcileMessage, balanceTransaction, paymentSourceTransfer);

        var paymentEvent = new PaymentIncludedInPayout(paymentExternalId,
                payoutReconcileMessage.getGatewayPayoutId(),
                payoutReconcileMessage.getCreatedDate());
        emitEvent(paymentEvent, payoutReconcileMessage, paymentExternalId);

        LOGGER.info("Emitted event for payment [{}] included in payout [{}]", paymentExternalId, payoutReconcileMessage.getGatewayPayoutId());
    }

    private void emitRefundEvent(PayoutReconcileMessage payoutReconcileMessage, BalanceTransaction balanceTransaction) {
        var sourceTransfer = (Transfer) balanceTransaction.getSourceObject();
        String refundExternalId = resolveTransactionExternalId(payoutReconcileMessage, balanceTransaction, sourceTransfer);

        var refundEvent = new RefundIncludedInPayout(refundExternalId,
                payoutReconcileMessage.getGatewayPayoutId(),
                payoutReconcileMessage.getCreatedDate());
        emitEvent(refundEvent, payoutReconcileMessage, refundExternalId);

        LOGGER.info("Emitted event for refund [{}] included in payout [{}]", refundExternalId, payoutReconcileMessage.getGatewayPayoutId());
    }

    private String resolveTransactionExternalId(PayoutReconcileMessage payoutReconcileMessage, BalanceTransaction balanceTransaction, Transfer sourceTransfer) {
        var metadata = StripeTransferMetadata.from(sourceTransfer.getMetadata());
        var transactionExternalId = metadata.getGovukPayTransactionExternalId();
        if (transactionExternalId == null) {
            throw new RuntimeException(format("Transaction external ID missing in metadata on Stripe transfer for " +
                            "balance transaction [%s] for payout [%s] for gateway account [%s]",
                    balanceTransaction.getId(), payoutReconcileMessage.getGatewayPayoutId(),
                    payoutReconcileMessage.getConnectAccountId()));
        }
        return transactionExternalId;
    }

    private void emitEvent(Event event, PayoutReconcileMessage payoutReconcileMessage, String transactionExternalId) {
        if (connectorConfiguration.getEmitPayoutEvents()) {
            try {
                eventService.emitEvent(event, false);
            } catch (QueueException e) {
                throw new RuntimeException(format("Error sending %s event for transaction [%s] included in payout [%s] to event queue: %s",
                        event.getEventType(), transactionExternalId, payoutReconcileMessage.getGatewayPayoutId(), e.getMessage()), e);
            }
        }
    }
}
