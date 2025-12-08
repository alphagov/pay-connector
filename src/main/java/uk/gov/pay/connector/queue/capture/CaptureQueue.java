package uk.gov.pay.connector.queue.capture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.gson.GsonBuilder;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.service.payments.commons.queue.exception.QueueException;
import uk.gov.service.payments.commons.queue.model.QueueMessage;
import uk.gov.service.payments.commons.queue.sqs.AbstractQueue;
import uk.gov.service.payments.commons.queue.sqs.SqsQueueService;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class CaptureQueue extends AbstractQueue {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public CaptureQueue(
            SqsQueueService sqsQueueService,
            ConnectorConfiguration connectorConfiguration, ObjectMapper objectMapper) {
        super(sqsQueueService, objectMapper,
                connectorConfiguration.getSqsConfig().getCaptureQueueUrl(),
                connectorConfiguration.getCaptureProcessConfig()
                        .getFailedCaptureRetryDelayInSeconds());
    }

    public void sendForCapture(ChargeEntity charge) throws QueueException {
        String message = new GsonBuilder()
                .create()
                .toJson(ImmutableMap.of("chargeId", charge.getExternalId()));

        QueueMessage queueMessage = sendMessageToQueue(message);

        logger.info("Charge [{}] added to capture queue. Message ID [{}]", charge.getExternalId(), queueMessage.getMessageId());
    }

    public List<ChargeCaptureMessage> retrieveChargesForCapture() throws QueueException {
        List<QueueMessage> queueMessages = retrieveMessages();

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
}
