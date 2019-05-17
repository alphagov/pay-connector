package uk.gov.pay.connector.queue.managed;

import io.dropwizard.lifecycle.Managed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.queue.QueueException;
import uk.gov.pay.connector.queue.QueueMessage;
import uk.gov.pay.connector.queue.sqs.SqsQueueService;

import java.util.List;

import javax.inject.Inject;

// @TODO(sfount) do we need healthchecks for Managed objects
public class SQSMessageReceiverHandler implements Managed {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SQSMessageReceiverHandler.class);

    // @TODO(sfount) move queue URL logic to CaptureQueue when available
    private String queueUrl;
   
    // @TODO(sfount) run on dropwizard thread executor
    private Thread messageReceiverThread;
    private SqsQueueService sqsQueueService;
  
    @Inject
    public SQSMessageReceiverHandler(ConnectorConfiguration configuration, SqsQueueService sqsQueueService) {
        // Environment environment; environment.lifecycle()
        this.sqsQueueService = sqsQueueService;
        this.queueUrl = configuration.getSqsConfig().getCaptureQueueUrl(); 
    }
    
    @Override
    public void start() throws Exception { 
        messageReceiverThread = receiver(this.queueUrl);
        messageReceiverThread.start();
    }
    
    @Override
    public void stop() throws Exception { 
        messageReceiverThread.interrupt();
    }
   
    @Inject
    private Thread receiver(String queueUrl) { 
        return new Thread() { 
            @Override
            public void run() {
                LOGGER.info("SQS message receiver short polling queue");
                while(!isInterrupted()) {
                    try {
                        List<QueueMessage> messages = sqsQueueService.receiveMessages(queueUrl);
                        for (QueueMessage message: messages) {
                            LOGGER.info("SQS message received [{}] - {}", message.getMessageId(), message.getMessageBody());
                            sqsQueueService.deleteMessage(queueUrl, message);
                    }
                    } catch (QueueException e) {
                        LOGGER.error("Queue exception [{}]", e);
                    }
                }
            }
        };
    }
}
