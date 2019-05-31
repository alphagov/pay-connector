package uk.gov.pay.connector.queue;

import com.google.common.collect.ImmutableMap;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.queue.sqs.SqsQueueService;
import uk.gov.pay.connector.util.JsonObjectMapper;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class CaptureQueue {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JsonObjectMapper jsonObjectMapper;

    private final String captureQueueUrl;
    private final int failedCaptureRetryDelayInSeconds;
    private SqsQueueService sqsQueueService;

    // default message keyword `All`, can be made more granular if queue is responsible for multiple message types
    private static final String CAPTURE_MESSAGE_ATTRIBUTE_NAME = "All";

    @Inject
    public CaptureQueue(
            SqsQueueService sqsQueueService,
            ConnectorConfiguration connectorConfiguration, JsonObjectMapper jsonObjectMapper) {
        this.sqsQueueService = sqsQueueService;
        this.captureQueueUrl = connectorConfiguration.getSqsConfig().getCaptureQueueUrl();
        this.failedCaptureRetryDelayInSeconds = connectorConfiguration.getCaptureProcessConfig().getFailedCaptureRetryDelayInSeconds();
        this.jsonObjectMapper = jsonObjectMapper;
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
            CaptureCharge captureCharge = jsonObjectMapper
                    .getObject(qm.getMessageBody(), CaptureCharge.class);
            return ChargeCaptureMessage.of(captureCharge, qm);
        } catch (WebApplicationException e) {
            logger.warn("Error parsing the charge capture message from queue [{}]", e.getMessage());
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
