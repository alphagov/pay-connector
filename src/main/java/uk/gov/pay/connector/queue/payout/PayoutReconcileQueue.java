package uk.gov.pay.connector.queue.payout;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.queue.QueueException;
import uk.gov.pay.connector.queue.QueueMessage;
import uk.gov.pay.connector.queue.sqs.SqsQueueService;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class PayoutReconcileQueue {

    private static final String MESSAGE_ATTRIBUTES_TO_RECEIVE = "All";
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ObjectMapper objectMapper;
    private final String payoutReconcileQueueUrl;
    private final int failedPayoutReconcileRetryDelayInSeconds;
    private SqsQueueService sqsQueueService;

    @Inject
    public PayoutReconcileQueue(
            SqsQueueService sqsQueueService,
            ConnectorConfiguration connectorConfiguration, ObjectMapper objectMapper) {
        this.sqsQueueService = sqsQueueService;
        this.payoutReconcileQueueUrl = connectorConfiguration.getSqsConfig().getPayoutReconcileQueueUrl();
        this.failedPayoutReconcileRetryDelayInSeconds = connectorConfiguration.getPayoutReconcileProcessConfig()
                .getFailedPayoutReconcileMessageRetryDelayInSeconds();
        this.objectMapper = objectMapper;
    }

    public void sendPayout(Payout payout) throws QueueException, JsonProcessingException {
        String message = objectMapper.writeValueAsString(payout);

        QueueMessage queueMessage = sqsQueueService.sendMessage(payoutReconcileQueueUrl, message);

        logger.info("Payout [{}] added to queue. Message ID [{}]", payout.getGatewayPayoutId(), queueMessage.getMessageId());
    }

    public List<PayoutReconcileMessage> retrievePayoutMessages() throws QueueException {
        List<QueueMessage> queueMessages = sqsQueueService
                .receiveMessages(this.payoutReconcileQueueUrl, MESSAGE_ATTRIBUTES_TO_RECEIVE);

        return queueMessages
                .stream()
                .map(this::getPayoutReconcileMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private PayoutReconcileMessage getPayoutReconcileMessage(QueueMessage qm) {
        try {
            Payout payout = objectMapper.readValue(qm.getMessageBody(), Payout.class);

            return PayoutReconcileMessage.of(payout, qm);
        } catch (IOException e) {
            logger.warn("Error parsing payout message [message={}] from queue [error={}]", qm.getMessageBody(), e.getMessage());
            return null;
        }
    }

    public void markMessageAsProcessed(PayoutReconcileMessage message) throws QueueException {
        sqsQueueService.deleteMessage(this.payoutReconcileQueueUrl, message.getQueueMessageReceiptHandle());
    }

    public void scheduleMessageForRetry(PayoutReconcileMessage message) throws QueueException {
        sqsQueueService.deferMessage(this.payoutReconcileQueueUrl, message.getQueueMessageReceiptHandle(), failedPayoutReconcileRetryDelayInSeconds);
    }
}
