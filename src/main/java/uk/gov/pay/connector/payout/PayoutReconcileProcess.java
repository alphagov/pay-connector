package uk.gov.pay.connector.payout;

import com.stripe.model.Charge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
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
    private GatewayAccountDao gatewayAccountDao;

    @Inject
    public PayoutReconcileProcess(PayoutReconcileQueue payoutReconcileQueue,
                                  StripeClientWrapper stripeClientWrapper,
                                  StripeGatewayConfig stripeGatewayConfig,
                                  GatewayAccountDao gatewayAccountDao) {
        this.payoutReconcileQueue = payoutReconcileQueue;
        this.stripeClientWrapper = stripeClientWrapper;
        this.stripeGatewayConfig = stripeGatewayConfig;
        this.gatewayAccountDao = gatewayAccountDao;
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
                                    var paymentSource = (Charge) balanceTransaction.getSourceObject();
                                    var paymentSourceTransfer = paymentSource.getSourceTransferObject();
                                    String paymentExternalId = paymentSourceTransfer.getTransferGroup();
                                    
                                    // TODO emit transaction event
                                    LOGGER.info("Payout [{}] includes payment [{}]", payoutReconcileMessage.getGatewayPayoutId(), paymentExternalId);
                                    payments.getAndIncrement();
                                    break;
                                // Refunds have a balance transaction of type "transfer" as refunds are made from our 
                                // Platform Stripe account, and then a transfer is made for the amount from the connect
                                // account.
                                case "transfer":
                                    String transferId = balanceTransaction.getSource();
                                    
                                    // TODO emit transaction event
                                    LOGGER.info("Payout [{}] includes transfer (refund) [{}]", payoutReconcileMessage.getGatewayPayoutId(), transferId);
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
                LOGGER.warn("Error processing payout from SQS message [queueMessageId={}] [errorMessage={}]",
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
                .orElseThrow(() -> new GatewayAccountNotFoundException(format("Gateway account with Stripe connect account ID %s not found.", stripeAccountId)));
    }
}
