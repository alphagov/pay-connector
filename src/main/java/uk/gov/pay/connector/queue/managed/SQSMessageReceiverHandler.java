package uk.gov.pay.connector.queue.managed;

import io.dropwizard.lifecycle.Managed;

import io.dropwizard.setup.Environment;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.paymentprocessor.service.CardCaptureMessageProcess;
import uk.gov.pay.connector.queue.QueueException;
import uk.gov.pay.connector.queue.QueueMessage;
import uk.gov.pay.connector.queue.sqs.SqsQueueService;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

// @TODO(sfount) do we need healthchecks for Managed objects
public class SQSMessageReceiverHandler implements Managed {

    private static final Logger LOGGER = LoggerFactory.getLogger(SQSMessageReceiverHandler.class);

    // @TODO(sfount) move queue URL logic to CaptureQueue when available
    private String queueUrl;
   
    // @TODO(sfount) run on dropwizard thread executor
    private Thread messageReceiverThread;
    private SqsQueueService sqsQueueService;
    
    private ScheduledExecutorService scheduledExecutorService;
    
    private CardCaptureMessageProcess cardCaptureMessageProcess;
    
    private String SQS_MESSAGE_RECEIVER_HANDLER_NAME = "sqs-receiver-handler";
    private int TOTAL_THREADS = 1;
  
    @Inject
    public SQSMessageReceiverHandler(ConnectorConfiguration configuration, 
                                     SqsQueueService sqsQueueService, 
                                     Environment environment,
                                     CardCaptureMessageProcess cardCaptureMessageProcess) {
        // Environment environment; environment.lifecycle()
//        this.sqsQueueService = sqsQueueService;
//        this.queueUrl = configuration.getSqsConfig().getCaptureQueueUrl(); 
        this.cardCaptureMessageProcess = cardCaptureMessageProcess;
        
        scheduledExecutorService = environment
                .lifecycle()
                .scheduledExecutorService(SQS_MESSAGE_RECEIVER_HANDLER_NAME)
                .threads(TOTAL_THREADS)
                .build();
    }
    
    @Override
    public void start() throws Exception { 
//        messageReceiverThread = receiver(this.queueUrl);
//        messageReceiverThread.start();
        int INITIAL_DELAY = 1;
        int DELAY = 1; 
        scheduledExecutorService.scheduleWithFixedDelay(
                receiver(this.queueUrl), 
                INITIAL_DELAY, 
                DELAY, 
                TimeUnit.SECONDS);
        
    }
    
    @Override
    public void stop() throws Exception { 
        scheduledExecutorService.shutdown();
//        messageReceiverThread.interrupt();
    }
   
    private Thread receiver(String queueUrl) { 
        return new Thread() { 
            @Override
            public void run() {
                LOGGER.info("SQS message receiver short polling queue");
                while(!isInterrupted()) {
                    try {
                        List<QueueMessage> messages = sqsQueueService.receiveMessages(queueUrl);
                        cardCaptureMessageProcess.runCapture(messages);
                    } catch (QueueException e) {
                        LOGGER.error("Queue exception [{}]", e);
                    }
                }
            }
        };
    }
}
