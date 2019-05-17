package uk.gov.pay.connector.queue.managed;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import io.dropwizard.lifecycle.Managed;

import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.queue.QueueException;
import uk.gov.pay.connector.queue.QueueMessage;
import uk.gov.pay.connector.queue.sqs.SqsQueueService;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

public class SQSMessageReceiverHandler implements Managed {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SQSMessageReceiverHandler.class);
    
    
    // @TODO(sfount) move to seperate process class
    // @TODO(sfount) use capture queue abstraction when it is availble in master
    // private SqsQueueService sqsQueueService;
    private AmazonSQS sqsClient; 
    private String queueUrl;
    
    // simple single thread used to start
    private Thread messageReceiverThread;
    
    private SqsQueueService sqsQueueService;
    private ConnectorConfiguration configuration;
  
    @Inject
    public SQSMessageReceiverHandler(ConnectorConfiguration configuration, Environment environment, SqsQueueService sqsQueueService) {
        this.sqsQueueService = sqsQueueService;
        this.configuration = configuration;
        LOGGER.info("SQS Message receiver got [{}]", this.sqsQueueService);
        
        // @TODO(sfount) move to capture queue abstraction when is available in master
        // this should use the singleton client
//        this.sqsClient = AmazonSQSClientBuilder.standard()
//                .withEndpointConfiguration(
//                        new AwsClientBuilder.EndpointConfiguration(
//                                configuration.getSqsConfig().getEndpoint(),
//                                configuration.getSqsConfig().getRegion())
//                        ).build();
        this.queueUrl = configuration.getSqsConfig().getCaptureQueueUrl(); 
    }
    
    @Override
    public void start() throws Exception { 
        // @TODO(sfount) inline thread - could be replaced with a scheduled set of threads 
        // service = Executors.newScheduledThreadPool(5) 
        // service.scheduleAtFixedRate(new SQSMessageProcess(), 1, 1, TimeUnit.SECONDS);
        messageReceiverThread = receiver(this.queueUrl);
        messageReceiverThread.start();
    }
    
    @Override
    public void stop() throws Exception { 
        // service.shutdown(); 
    }
   
    @Inject
    private Thread receiver(String queueUrl) { 
        return new Thread() { 
            @Override
            public void run() {
                LOGGER.info("sfount thread started");
                while(!isInterrupted()) {
                    // LOGGER.info("sfount thread listening for messages on queue [{}]", queueUrl);
//                    ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(queueUrl);
                    
//                    receiveMessageRequest.setMaxNumberOfMessages(10);
//                    List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest.withMessageAttributeNames("All")).getMessages();
                    try {
                        List<QueueMessage> messages = sqsQueueService.receiveMessages(queueUrl);
                        for (QueueMessage message: messages) {
                            LOGGER.info("message details [{}] - {}", message.getMessageId(), message.getMessageBody());
                    }
                    } catch (QueueException e) {
                        LOGGER.error("Queue exception [{}]", e);
                    }
                }
            }
        };
    }
    
    // processMessage (paymentprocesser/service/CardCaptureProcess)
    
    // deleteMessage - this class; inform SQS that message has been processed
    
    // @TODO(sfount) do we need healthchecks for Managed object
}
