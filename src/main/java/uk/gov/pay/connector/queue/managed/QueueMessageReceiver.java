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

    private static final String SQS_MESSAGE_RECEIVER_THREAD_NAME = "sqs-message-chargeCaptureMessageReceiver";

    private static final Logger LOGGER = LoggerFactory.getLogger(QueueMessageReceiver.class);

    private final int queueSchedulerThreadDelayInSeconds;

    private ScheduledExecutorService chargeCaptureMessageExecutorService;
    private ScheduledExecutorService stateTransitionMessageExecutorService;

    private final CardCaptureProcess cardCaptureProcess;
    private final PaymentStateTransitionEmitterProcess paymentStateTransitionEmitterProcess;


    @Inject
    public QueueMessageReceiver(CardCaptureProcess cardCaptureProcess, PaymentStateTransitionEmitterProcess paymentStateTransitionEmitterProcess,
                                Environment environment, ConnectorConfiguration connectorConfiguration) {
        this.paymentStateTransitionEmitterProcess = paymentStateTransitionEmitterProcess;
        this.cardCaptureProcess = cardCaptureProcess;

        int queueScheduleNumberOfThreads = connectorConfiguration.getCaptureProcessConfig().getQueueSchedulerNumberOfThreads();

        chargeCaptureMessageExecutorService = environment
                .lifecycle()
                .scheduledExecutorService(SQS_MESSAGE_RECEIVER_THREAD_NAME)
                .threads(queueScheduleNumberOfThreads)
                .build();

        stateTransitionMessageExecutorService = environment
                .lifecycle()
                .scheduledExecutorService("payment-state-transition-message-poller")
                .threads(1)
                .build();

        queueSchedulerThreadDelayInSeconds = connectorConfiguration.getCaptureProcessConfig().getQueueSchedulerThreadDelayInSeconds();
    }

    @Override
    public void start() {
        int initialDelay = queueSchedulerThreadDelayInSeconds;
        chargeCaptureMessageExecutorService.scheduleWithFixedDelay(
                this::chargeCaptureMessageReceiver,
                initialDelay,
                queueSchedulerThreadDelayInSeconds,
                TimeUnit.SECONDS);

        stateTransitionMessageExecutorService.scheduleWithFixedDelay(
                this::stateTransitionMessageReceiver,
                0,
                100,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        chargeCaptureMessageExecutorService.shutdown();
        stateTransitionMessageExecutorService.shutdown();
    }

    private void stateTransitionMessageReceiver() {
        try {
            paymentStateTransitionEmitterProcess.handleStateTransitionMessages();
        } catch (Exception e) {
            LOGGER.error("State transition message polling thread failed for [exception={}]", e);
        }
    }

    private void chargeCaptureMessageReceiver() {
        try {
            cardCaptureProcess.handleCaptureMessages();
        } catch (Exception e) {
            LOGGER.error("Queue message chargeCaptureMessageReceiver thread exception [{}]", e);
        }
    }
}
