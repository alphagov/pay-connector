package uk.gov.pay.connector.queue.managed;

import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.events.dao.PaymentStateTransitionEventEmitterProcess;
import uk.gov.pay.connector.paymentprocessor.service.CardCaptureProcess;

import javax.inject.Inject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class QueueMessageReceiver implements Managed {

    private static final String MESSAGE_RECEIVER_THREAD_NAME = "message-receiver";

    private static final Logger LOGGER = LoggerFactory.getLogger(QueueMessageReceiver.class);

    private final int queueSchedulerThreadDelayInSeconds;

    private ScheduledExecutorService scheduledExecutorService;
    private CardCaptureProcess cardCaptureProcess;
    private PaymentStateTransitionEventEmitterProcess paymentStateTransitionEventEmitterProcess;

    @Inject
    public QueueMessageReceiver(CardCaptureProcess cardCaptureProcess,
                                Environment environment, ConnectorConfiguration connectorConfiguration) {

        int queueScheduleNumberOfThreads = connectorConfiguration.getCaptureProcessConfig().getQueueSchedulerNumberOfThreads();

        this.cardCaptureProcess = cardCaptureProcess;

        scheduledExecutorService = environment
                .lifecycle()
                .scheduledExecutorService(MESSAGE_RECEIVER_THREAD_NAME)
                .threads(queueScheduleNumberOfThreads)
                .build();
        queueSchedulerThreadDelayInSeconds = connectorConfiguration.getCaptureProcessConfig().getQueueSchedulerThreadDelayInSeconds();
    }

    @Override
    public void start() {
        int initialDelay = queueSchedulerThreadDelayInSeconds;
        scheduledExecutorService.scheduleWithFixedDelay(
                sqsReceiver(),
                initialDelay,
                queueSchedulerThreadDelayInSeconds,
                TimeUnit.SECONDS);

        scheduledExecutorService.scheduleWithFixedDelay(
                paymentStateTransitionPoller(),
                initialDelay,
                queueSchedulerThreadDelayInSeconds,
                TimeUnit.SECONDS
        );
    }

    @Override
    public void stop() {
        scheduledExecutorService.shutdown();
    }

    private Thread sqsReceiver() {
        return new Thread() {
            @Override
            public void run() {
                LOGGER.info("Queue message receiver thread polling queue");
                while (!isInterrupted()) {
                    try {
                        cardCaptureProcess.handleCaptureMessages();
                    } catch (Exception e) {
                        LOGGER.error("Queue message receiver thread exception [{}]", e);
                    }
                }
            }
        };
    }

    private Thread paymentStateTransitionPoller() {
        return new Thread() {
            @Override
            public void run() {
                while(!isInterrupted()) {

                }
            }
        };
    }
}
