package uk.gov.pay.connector.queue;

import com.google.common.collect.ImmutableMap;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.paymentprocessor.service.CardCaptureMessageProcess;
import uk.gov.pay.connector.queue.sqs.SqsQueueService;

import javax.inject.Inject;
import java.util.List;

public class CaptureQueue {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final String captureQueueUrl;
    private SqsQueueService sqsQueueService;
    
    private CardCaptureMessageProcess cardCaptureMessageProcess;

    // @TODO(sfount) capture specific message attribute
    private String CAPTURE_MESSAGE_ATTRIBUTE_NAME = "All";

    @Inject
    public CaptureQueue(
            SqsQueueService sqsQueueService,
            ConnectorConfiguration connectorConfiguration,
            CardCaptureMessageProcess cardCaptureMessageProcess) {
        this.sqsQueueService = sqsQueueService;
        this.captureQueueUrl = connectorConfiguration.getSqsConfig().getCaptureQueueUrl();
        this.cardCaptureMessageProcess = cardCaptureMessageProcess;
    }

    public void sendForCapture(String externalId) throws QueueException {

        String message = new GsonBuilder()
                .create()
                .toJson(ImmutableMap.of("chargeId", externalId));

        QueueMessage queueMessage = sqsQueueService.sendMessage(captureQueueUrl, message);

        logger.info("Charge [{}] added to capture queue. Message ID [{}]", externalId, queueMessage.getMessageId());
    }
    
    public List<QueueMessage> receiveCaptureMessages() throws QueueException {
        return sqsQueueService.receiveMessages(this.captureQueueUrl, CAPTURE_MESSAGE_ATTRIBUTE_NAME);
    }
    
    public void markMessageAsProcessed(QueueMessage message) throws QueueException {    
        sqsQueueService.deleteMessage(this.captureQueueUrl, message.getReceiptHandle());
    }
}
