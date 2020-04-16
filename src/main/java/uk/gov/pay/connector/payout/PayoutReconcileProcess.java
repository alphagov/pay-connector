package uk.gov.pay.connector.payout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.queue.QueueException;
import uk.gov.pay.connector.queue.payout.PayoutReconcileMessage;
import uk.gov.pay.connector.queue.payout.PayoutReconcileQueue;

import javax.inject.Inject;
import java.util.List;

public class PayoutReconcileProcess {

    private static final Logger LOGGER = LoggerFactory.getLogger(PayoutReconcileProcess.class);
    private PayoutReconcileQueue payoutReconcileQueue;

    @Inject
    public PayoutReconcileProcess(PayoutReconcileQueue payoutReconcileQueue) {
        this.payoutReconcileQueue = payoutReconcileQueue;
    }

    public void processPayouts() throws QueueException {
        List<PayoutReconcileMessage> payoutReconcileMessages = payoutReconcileQueue.retrievePayoutMessages();
        for (PayoutReconcileMessage payoutReconcileMessage : payoutReconcileMessages) {
            try {
                LOGGER.info("Processing payout [{}] for connect account [{}]",
                        payoutReconcileMessage.getGatewayPayoutId(),
                        payoutReconcileMessage.getConnectAccountId());

                //TODO: Payout reconciliation - Algorithm described in PP-6266

                LOGGER.info("Finished processing payout [{}] for connect account [{}]",
                        payoutReconcileMessage.getGatewayPayoutId(),
                        payoutReconcileMessage.getConnectAccountId());

                payoutReconcileQueue.markMessageAsProcessed(payoutReconcileMessage);
            } catch (Exception e) {
                LOGGER.warn("Error processing payout from SQS message [queueMessageId={}] [errorMessage={}]",
                        payoutReconcileMessage.getQueueMessageId(),
                        e.getMessage()
                );
            }
        }
    }
}
