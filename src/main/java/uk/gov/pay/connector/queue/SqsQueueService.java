package uk.gov.pay.connector.queue;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.AmazonSQSException;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SqsQueueService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private AmazonSQS sqsClient;

    public SqsQueueService(AmazonSQS sqsClient) {
        this.sqsClient = sqsClient;
    }

    public void sendMessage(String queueUrl, String messageBody) {
        try {
            SendMessageResult sendMessageResult = sqsClient.sendMessage(queueUrl, messageBody);

            logger.info("Message sent to SQS queue - {}", sendMessageResult);
        } catch (AmazonSQSException | UnsupportedOperationException e) {
            logger.error("Failed sending message to SQS queue - {}", e.getMessage());
            throw new SqsQueueOperationException(e.getMessage());
        }
    }

    public List<SqsQueueReceiveResponse> receiveMessages(String queueUrl) {
        try {
            ReceiveMessageResult receiveMessageResult = sqsClient.receiveMessage(queueUrl);

            return SqsQueueReceiveResponse.of(receiveMessageResult);
        } catch (AmazonSQSException | UnsupportedOperationException e) {
            logger.error("Failed to receive messages from SQS queue - {}", e.getMessage());
            throw new SqsQueueOperationException(e.getMessage());
        }
    }

}
