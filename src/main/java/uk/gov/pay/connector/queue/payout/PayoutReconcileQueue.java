package uk.gov.pay.connector.queue.payout;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.service.payments.commons.queue.exception.QueueException;
import uk.gov.service.payments.commons.queue.model.QueueMessage;
import uk.gov.service.payments.commons.queue.sqs.AbstractQueue;
import uk.gov.service.payments.commons.queue.sqs.SqsQueueService;

import jakarta.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class PayoutReconcileQueue extends AbstractQueue {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public PayoutReconcileQueue(SqsQueueService sqsQueueService,
                                ConnectorConfiguration connectorConfiguration,
                                ObjectMapper objectMapper) {
        super(sqsQueueService, objectMapper,
                connectorConfiguration.getSqsConfig().getPayoutReconcileQueueUrl(),
                connectorConfiguration.getPayoutReconcileProcessConfig()
                        .getFailedPayoutReconcileMessageRetryDelayInSeconds());
    }

    public void sendPayout(Payout payout) throws QueueException, JsonProcessingException {
        String message = objectMapper.writeValueAsString(payout);
        QueueMessage queueMessage = sendMessageToQueue(message);
        logger.info("Payout [{}] added to queue. Message ID [{}]",
                payout.getGatewayPayoutId(), queueMessage.getMessageId());
    }

    public List<PayoutReconcileMessage> retrievePayoutMessages() throws QueueException {
        List<QueueMessage> queueMessages = retrieveMessages();

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
            logger.warn("Error parsing payout message [message={}] from queue [error={}]",
                    qm.getMessageBody(), e.getMessage());
            return null;
        }
    }
}
