package uk.gov.pay.connector.queue;

import com.google.common.collect.ImmutableMap;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.queue.sqs.SqsQueueService;

import javax.inject.Inject;

public class CaptureQueue {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final String captureQueueUrl;
    private SqsQueueService sqsQueueService;

    @Inject
    public CaptureQueue(SqsQueueService sqsQueueService, ConnectorConfiguration connectorConfiguration) {
        this.sqsQueueService = sqsQueueService;
        this.captureQueueUrl = connectorConfiguration.getSqsConfig().getCaptureQueueUrl();
    }

    public void sendForCapture(String externalId) throws QueueException {

        String message = new GsonBuilder()
                .create()
                .toJson(ImmutableMap.of("chargeId", externalId));

        QueueMessage queueMessage = sqsQueueService.sendMessage(captureQueueUrl, message);

        logger.info("Charge [{}] added to capture queue. Message ID [{}]", externalId, queueMessage.getMessageId());
    }


}
