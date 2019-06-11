package uk.gov.pay.connector.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.queue.sqs.SqsQueueService;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class CaptureQueue {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ObjectMapper objectMapper;

    private final String captureQueueUrl;
    private final int failedCaptureRetryDelayInSeconds;
    private SqsQueueService sqsQueueService;

    // default message keyword `All`, can be made more granular if queue is responsible for multiple message types
    private static final String CAPTURE_MESSAGE_ATTRIBUTE_NAME = "All";

    @Inject
    public CaptureQueue(
            SqsQueueService sqsQueueService,
            ConnectorConfiguration connectorConfiguration, ObjectMapper objectMapper) {
        this.sqsQueueService = sqsQueueService;
        this.captureQueueUrl = connectorConfiguration.getSqsConfig().getCaptureQueueUrl();
        this.failedCaptureRetryDelayInSeconds = connectorConfiguration.getCaptureProcessConfig().getFailedCaptureRetryDelayInSeconds();
        this.objectMapper = objectMapper;
    }

    public void sendForCapture(ChargeEntity charge) throws QueueException {
        String message = new GsonBuilder()
                .create()
                .toJson(ImmutableMap.of("chargeId", charge.getExternalId()));

        QueueMessage queueMessage = sqsQueueService.sendMessage(captureQueueUrl, message);

        logger.info("Charge [{}] added to capture queue. Message ID [{}]", charge.getExternalId(), queueMessage.getMessageId());
    }

    public List<ChargeCaptureMessage> retrieveChargesForCapture() throws QueueException {
        List<QueueMessage> queueMessages = sqsQueueService
                .receiveMessages(this.captureQueueUrl, CAPTURE_MESSAGE_ATTRIBUTE_NAME);

        return queueMessages
                .stream()
                .map(this::getChargeCaptureMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private ChargeCaptureMessage getChargeCaptureMessage(QueueMessage qm) {
        try {
            CaptureCharge captureCharge = objectMapper.readValue(qm.getMessageBody(), CaptureCharge.class);

            return ChargeCaptureMessage.of(captureCharge, qm);
        } catch (IOException e) {
            logger.warn("Error parsing the charge capture message [message={}] from queue [error={}]", qm.getMessageBody(), e.getMessage());
            return null;
        }
    }

    public void markMessageAsProcessed(ChargeCaptureMessage message) throws QueueException {
        sqsQueueService.deleteMessage(this.captureQueueUrl, message.getQueueMessageReceiptHandle());
    }

    public void scheduleMessageForRetry(ChargeCaptureMessage message) throws QueueException {
        sqsQueueService.deferMessage(this.captureQueueUrl, message.getQueueMessageReceiptHandle(), failedCaptureRetryDelayInSeconds);
    }
}
