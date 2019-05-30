package uk.gov.pay.connector.queue.managed;

import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.paymentprocessor.service.CardCaptureMessageProcess;
import uk.gov.pay.connector.queue.QueueException;

import javax.inject.Inject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class QueueMessageReceiver implements Managed {

    private static final String SQS_MESSAGE_RECEIVER_THREAD_NAME = "sqs-message-receiver";

    private static final Logger LOGGER = LoggerFactory.getLogger(QueueMessageReceiver.class);

    private final int queueSchedulerThreadDelayInSeconds;

    private ScheduledExecutorService scheduledExecutorService;
    private CardCaptureMessageProcess cardCaptureMessageProcess;

    @Inject
    public QueueMessageReceiver(CardCaptureMessageProcess cardCaptureMessageProcess,
                                Environment environment, ConnectorConfiguration connectorConfiguration) {

        int queueScheduleNumberOfThreads = connectorConfiguration.getCaptureProcessConfig().getQueueSchedulerNumberOfThreads();

        this.cardCaptureMessageProcess = cardCaptureMessageProcess;

        scheduledExecutorService = environment
                .lifecycle()
                .scheduledExecutorService(SQS_MESSAGE_RECEIVER_THREAD_NAME)
                .threads(queueScheduleNumberOfThreads)
                .build();
        queueSchedulerThreadDelayInSeconds = connectorConfiguration.getCaptureProcessConfig().getQueueSchedulerThreadDelayInSeconds();
    }

    @Override
    public void start() {
        int initialDelay = queueSchedulerThreadDelayInSeconds;
        scheduledExecutorService.scheduleWithFixedDelay(
                receiver(),
                initialDelay,
                queueSchedulerThreadDelayInSeconds,
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
                LOGGER.info("Queue message receiver thread polling queue");
                while (!isInterrupted()) {
                    try {
                        cardCaptureMessageProcess.handleCaptureMessages();
                    } catch (QueueException e) {
                        LOGGER.error("Queue message receiver thread exception [{}]", e);
                    }
                }
            }
        };
    }
}
