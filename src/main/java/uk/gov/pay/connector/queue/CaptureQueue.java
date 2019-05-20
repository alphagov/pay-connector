package uk.gov.pay.connector.queue;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.gateway.CaptureResponse;
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
    
    public void receiveCaptureMessages() throws QueueException {
        List<QueueMessage> captureMessages = sqsQueueService.receiveMessages(this.captureQueueUrl, CAPTURE_MESSAGE_ATTRIBUTE_NAME);
        for (QueueMessage message: captureMessages) {
            try {
                logger.info("SQS message received [{}] - {}", message.getMessageId(), message.getMessageBody());

                String externalChargeId = getExternalChargeIdFromMessage(message);

                CaptureResponse gatewayResponse = cardCaptureMessageProcess.runCapture(externalChargeId);
                
                if (gatewayResponse.isSuccessful()) {
                    sqsQueueService.deleteMessage(this.captureQueueUrl, message);
                } else {
                    logger.info(
                            "Failed to capture [messageBody={}] due to: {}",
                            message.getMessageBody(),
                            gatewayResponse.getError().get().getMessage()
                    );
                }
            } catch (Exception e) {
                logger.warn("Error capturing charge from SQS message [{}]", e);
            }
        }
    }

    private String getExternalChargeIdFromMessage(QueueMessage message) {
        JsonObject captureObject = new Gson().fromJson(message.getMessageBody(), JsonObject.class);
        return captureObject.get("chargeId").getAsString();
    }


}
