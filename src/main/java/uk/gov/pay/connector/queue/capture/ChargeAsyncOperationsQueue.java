package uk.gov.pay.connector.queue.capture;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.service.payments.commons.queue.exception.QueueException;
import uk.gov.service.payments.commons.queue.model.QueueMessage;
import uk.gov.service.payments.commons.queue.sqs.AbstractQueue;
import uk.gov.service.payments.commons.queue.sqs.SqsQueueService;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ChargeAsyncOperationsQueue extends AbstractQueue {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public ChargeAsyncOperationsQueue(
            SqsQueueService sqsQueueService,
            ConnectorConfiguration connectorConfiguration, ObjectMapper objectMapper) {
        super(sqsQueueService, objectMapper,
                connectorConfiguration.getSqsConfig().getCaptureQueueUrl(),
                connectorConfiguration.getChargeAsyncOperationsConfig()
                        .getFailedCaptureRetryDelayInSeconds());
    }

    public void sendForCapture(ChargeEntity charge) throws QueueException, JsonProcessingException {
        var operation = new AsyncChargeOperation(charge.getExternalId(), AsyncChargeOperationKey.CAPTURE);
        String message = objectMapper.writeValueAsString(operation);

        QueueMessage queueMessage = sendMessageToQueue(message);

        logger.info("Charge [{}] added to capture queue. Message ID [{}]", charge.getExternalId(), queueMessage.getMessageId());
    }

    public List<ChargeAsyncOperationsMessage> retrieveAsyncOperations() throws QueueException {
        List<QueueMessage> queueMessages = retrieveMessages();

        return queueMessages
                .stream()
                .map(this::getAsyncOperationsMessages)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private ChargeAsyncOperationsMessage getAsyncOperationsMessages(QueueMessage qm) {
        try {
            AsyncChargeOperation asyncChargeOperation = objectMapper.readValue(qm.getMessageBody(), AsyncChargeOperation.class);

            return ChargeAsyncOperationsMessage.of(asyncChargeOperation, qm);
        } catch (IOException e) {
            logger.warn("Error parsing the charge async operation message [message={}] from queue [error={}]", qm.getMessageBody(), e.getMessage());
            return null;
        }
    }
}
