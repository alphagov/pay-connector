package uk.gov.pay.connector.queue.sqs;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.DeleteMessageResult;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.AmazonSQSException;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityResult;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.queue.QueueException;
import uk.gov.pay.connector.queue.QueueMessage;

import javax.inject.Inject;
import java.util.List;

public class SqsQueueService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private AmazonSQS sqsClient;

    @Inject
    public SqsQueueService(AmazonSQS sqsClient) {
        this.sqsClient = sqsClient;
    }

    public QueueMessage sendMessage(String queueUrl, String messageBody) throws QueueException {
        try {
            SendMessageResult sendMessageResult = sqsClient.sendMessage(queueUrl, messageBody);

            logger.info("Message sent to SQS queue - {}", sendMessageResult);
            return QueueMessage.of(sendMessageResult, messageBody);
        } catch (AmazonSQSException | UnsupportedOperationException e) {
            logger.error("Failed sending message to SQS queue - {}", e.getMessage());
            throw new QueueException(e.getMessage());
        }
    }

    public List<QueueMessage> receiveMessages(String queueUrl, String messageAttributeName) throws QueueException {
        try {
            // @TODO(sfount) this should be configured in the connector environment
            int longPollingMaxWaitTime = 20;
            ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(queueUrl);
            receiveMessageRequest
                    .withMessageAttributeNames(messageAttributeName)
                    .withWaitTimeSeconds(longPollingMaxWaitTime)
                    .withMaxNumberOfMessages(10);

            ReceiveMessageResult receiveMessageResult = sqsClient.receiveMessage(receiveMessageRequest);

            return QueueMessage.of(receiveMessageResult);
        } catch (AmazonSQSException | UnsupportedOperationException e) {
            logger.error("Failed to receive messages from SQS queue - {}", e.getMessage());
            throw new QueueException(e.getMessage());
        }
    }

    public DeleteMessageResult deleteMessage(String queueUrl, String messageReceiptHandle) throws QueueException {
        try {
            return sqsClient.deleteMessage(new DeleteMessageRequest(queueUrl, messageReceiptHandle));
        } catch (AmazonSQSException | UnsupportedOperationException e) {
            logger.error("Failed to delete message from SQS queue - {}", e.getMessage());
            throw new QueueException(e.getMessage());
        }
    }

    public ChangeMessageVisibilityResult deferMessage(String queueUrl, String messageReceiptHandle, int timeoutInSeconds) throws QueueException {
        try {
            ChangeMessageVisibilityRequest changeMessageVisibilityRequest = new ChangeMessageVisibilityRequest(
                    queueUrl,
                    messageReceiptHandle,
                    timeoutInSeconds);

            return sqsClient.changeMessageVisibility(changeMessageVisibilityRequest);
        } catch (AmazonSQSException | UnsupportedOperationException e) {
            logger.error("Failed to defer message from SQS queue - {}", e.getMessage());
            throw new QueueException(e.getMessage());
        }
    }
}
