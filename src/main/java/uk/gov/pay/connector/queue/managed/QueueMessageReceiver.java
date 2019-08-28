package uk.gov.pay.connector.queue.managed;

import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.events.StateTransitionEmitterProcess;
import uk.gov.pay.connector.paymentprocessor.service.CardCaptureProcess;

import javax.inject.Inject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class QueueMessageReceiver implements Managed {

    private static final String SQS_MESSAGE_RECEIVER_THREAD_NAME = "sqs-message-chargeCaptureMessageReceiver";
    private static final int MAX_NUMBER_OF_SHUTDOWN_READINESS_CHECKS = 5;
    private static final int DELAY_BETWEEN_SHUTDOWN_CHECKS_IN_MILLISECONDS = 1000;

    private static final Logger LOGGER = LoggerFactory.getLogger(QueueMessageReceiver.class);

    private final int queueSchedulerThreadDelayInSeconds;
    private final int paymentStateTransitionPollerNumberOfThreads;


    private ScheduledExecutorService chargeCaptureMessageExecutorService;
    private ScheduledExecutorService stateTransitionMessageExecutorService;

    private final CardCaptureProcess cardCaptureProcess;
    private final StateTransitionEmitterProcess stateTransitionEmitterProcess;


    @Inject
    public QueueMessageReceiver(CardCaptureProcess cardCaptureProcess, StateTransitionEmitterProcess stateTransitionEmitterProcess,
                                Environment environment, ConnectorConfiguration connectorConfiguration) {
        this.stateTransitionEmitterProcess = stateTransitionEmitterProcess;
        this.cardCaptureProcess = cardCaptureProcess;

        int queueScheduleNumberOfThreads = connectorConfiguration.getCaptureProcessConfig().getQueueSchedulerNumberOfThreads();
        this.paymentStateTransitionPollerNumberOfThreads = connectorConfiguration.getEventQueueConfig().getPaymentStateTransitionPollerNumberOfThreads();

        chargeCaptureMessageExecutorService = environment
                .lifecycle()
                .scheduledExecutorService(SQS_MESSAGE_RECEIVER_THREAD_NAME)
                .threads(queueScheduleNumberOfThreads)
                .build();

        stateTransitionMessageExecutorService = environment
                .lifecycle()
                .scheduledExecutorService("payment-state-transition-message-poller-%d")
                .threads(paymentStateTransitionPollerNumberOfThreads)
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
            
        for (int i = 0; i < this.paymentStateTransitionPollerNumberOfThreads; i++) {
            stateTransitionMessageExecutorService.scheduleWithFixedDelay(
                    this::stateTransitionMessageReceiver, 1, 1, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void stop() {
        chargeCaptureMessageExecutorService.shutdown();
        stopStateTransitionExecutor();
    }

    private void stopStateTransitionExecutor() {
        int numberOfAttempts = 0;
        while (!stateTransitionEmitterProcess.isReadyForShutdown() && numberOfAttempts < MAX_NUMBER_OF_SHUTDOWN_READINESS_CHECKS) {
            LOGGER.info("State transition receiver is not ready for shutdown");
            numberOfAttempts++;

            try {
                Thread.sleep(DELAY_BETWEEN_SHUTDOWN_CHECKS_IN_MILLISECONDS);
            } catch (InterruptedException e) {
                handleInterruptedException();
            }
        }

        stateTransitionMessageExecutorService.shutdown();

        try {
            if (!stateTransitionMessageExecutorService.awaitTermination(2L, TimeUnit.SECONDS)) {
                stateTransitionMessageExecutorService.shutdownNow();
            }

            LOGGER.info("State transition receiver - number of not processed messages {}", stateTransitionEmitterProcess.getNumberOfNotProcessedMessages());
        } catch (InterruptedException e) {
            handleInterruptedException();
        }
    }

    private void handleInterruptedException() {
        stateTransitionMessageExecutorService.shutdownNow();
        LOGGER.info("State transition receiver - number of not processed messages {}", stateTransitionEmitterProcess.getNumberOfNotProcessedMessages());
        // Preserve interrupt status
        Thread.currentThread().interrupt();
    }

    private void stateTransitionMessageReceiver() {
        try {
            stateTransitionEmitterProcess.handleStateTransitionMessages();
        } catch (Exception e) {
            LOGGER.error("State transition message polling thread failed to process message due to [message={}]", e.getMessage());
        }
    }

    private void chargeCaptureMessageReceiver() {
        try {
            cardCaptureProcess.handleCaptureMessages();
        } catch (Exception e) {
            LOGGER.error("Queue message chargeCaptureMessageReceiver thread exception [message={}]", e.getMessage());
        }
    }
}
