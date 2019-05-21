package uk.gov.pay.connector.queue.managed;

import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.paymentprocessor.service.CardCaptureMessageProcess;
import uk.gov.pay.connector.queue.QueueException;

import javax.inject.Inject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class QueueMessageReceiver implements Managed {

    private final String SQS_MESSAGE_RECEIVER_NAME = "sqs-message-receiver";
    private final int TOTAL_MESSAGE_RECEIVER_THREADS = 1;

    private static final Logger LOGGER = LoggerFactory.getLogger(QueueMessageReceiver.class);

    private ScheduledExecutorService scheduledExecutorService;
    private CardCaptureMessageProcess cardCaptureMessageProcess;

    @Inject
    public QueueMessageReceiver(CardCaptureMessageProcess cardCaptureMessageProcess,
                                Environment environment) {
        this.cardCaptureMessageProcess = cardCaptureMessageProcess;

        scheduledExecutorService = environment
                .lifecycle()
                .scheduledExecutorService(SQS_MESSAGE_RECEIVER_NAME)
                .threads(TOTAL_MESSAGE_RECEIVER_THREADS)
                .build();
    }

    @Override
    public void start() {
        int INITIAL_DELAY = 1;
        int DELAY = 1;
        scheduledExecutorService.scheduleWithFixedDelay(
                receiver(),
                INITIAL_DELAY,
                DELAY,
                TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        scheduledExecutorService.shutdown();
    }

    private Thread receiver() {
        return new Thread() {
            @Override
            public void run() {
                LOGGER.info("SQS message receiver short polling queue");
                while (!isInterrupted()) {
                    try {
                        cardCaptureMessageProcess.handleCaptureMessages();
                    } catch (QueueException e) {
                        LOGGER.error("Queue exception [{}]", e);
                    }
                }
            }
        };
    }
}
