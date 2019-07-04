package uk.gov.pay.connector.queue.managed;

import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.events.PaymentStateTransitionEmitterProcess;
import uk.gov.pay.connector.paymentprocessor.service.CardCaptureProcess;

import javax.inject.Inject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class QueueMessageReceiver implements Managed {

    private static final String MESSAGE_RECEIVER_THREAD_NAME = "message-receiver";

    private static final Logger LOGGER = LoggerFactory.getLogger(QueueMessageReceiver.class);

    private final int queueSchedulerThreadDelayInSeconds;

    private ScheduledExecutorService captureQueueExecutorService;
    private ScheduledExecutorService paymentStateTransitionExecutorService;

    private CardCaptureProcess cardCaptureProcess;
    private PaymentStateTransitionEmitterProcess paymentStateTransitionEmitterProcess;

    @Inject
    public QueueMessageReceiver(CardCaptureProcess cardCaptureProcess,
                                PaymentStateTransitionEmitterProcess paymentStateTransitionEmitterProcess,
                                Environment environment, ConnectorConfiguration connectorConfiguration) {

        int queueScheduleNumberOfThreads = connectorConfiguration.getCaptureProcessConfig().getQueueSchedulerNumberOfThreads();

        this.cardCaptureProcess = cardCaptureProcess;

        captureQueueExecutorService = environment
                .lifecycle()
                .scheduledExecutorService(MESSAGE_RECEIVER_THREAD_NAME)
                .threads(queueScheduleNumberOfThreads)
                .build();

        paymentStateTransitionExecutorService = environment
                .lifecycle()
                .scheduledExecutorService("some-name-dot-com")
                .threads(1)
                .build();

        queueSchedulerThreadDelayInSeconds = connectorConfiguration.getCaptureProcessConfig().getQueueSchedulerThreadDelayInSeconds();
    }

    @Override
    public void start() {
        int initialDelay = queueSchedulerThreadDelayInSeconds;
        captureQueueExecutorService.scheduleWithFixedDelay(
                captureQueueReceiver(),
                initialDelay,
                queueSchedulerThreadDelayInSeconds,
                TimeUnit.SECONDS);

        paymentStateTransitionExecutorService.scheduleWithFixedDelay(
                paymentStateTransitionPoller(),
                0,
                100,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        captureQueueExecutorService.shutdown();
    }

    private Thread captureQueueReceiver() {
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
                    paymentStateTransitionEmitterProcess.handleStateTransitionMessages();
                }
            }
        };
    }
}
