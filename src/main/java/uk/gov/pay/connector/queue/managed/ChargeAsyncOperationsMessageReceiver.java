package uk.gov.pay.connector.queue.managed;

import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ChargeAsyncOperationsConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.paymentprocessor.service.ChargeAsyncOperationsProcess;

import javax.inject.Inject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ChargeAsyncOperationsMessageReceiver implements Managed {

    private static final String SQS_MESSAGE_RECEIVER_THREAD_NAME = "sqs-message-chargeAsyncOperationsMessageReceiver";

    private static final Logger LOGGER = LoggerFactory.getLogger(ChargeAsyncOperationsMessageReceiver.class);

    private final int queueSchedulerThreadDelayInSeconds;
    private final int queueSchedulerShutdownTimeoutInSeconds;
    private final ChargeAsyncOperationsProcess chargeAsyncOperationsProcess;
    private ScheduledExecutorService chargeAsyncOperationMessageExecutorService;

    @Inject
    public ChargeAsyncOperationsMessageReceiver(ChargeAsyncOperationsProcess chargeAsyncOperationsProcess, Environment environment,
                                                ConnectorConfiguration connectorConfiguration) {
        this.chargeAsyncOperationsProcess = chargeAsyncOperationsProcess;

        ChargeAsyncOperationsConfig chargeAsyncOperationsConfig = connectorConfiguration.getChargeAsyncOperationsConfig();
        int queueScheduleNumberOfThreads = chargeAsyncOperationsConfig
                .getQueueSchedulerNumberOfThreads();

        chargeAsyncOperationMessageExecutorService = environment
                .lifecycle()
                .scheduledExecutorService(SQS_MESSAGE_RECEIVER_THREAD_NAME)
                .threads(queueScheduleNumberOfThreads)
                .build();

        queueSchedulerThreadDelayInSeconds = chargeAsyncOperationsConfig.getQueueSchedulerThreadDelayInSeconds();
        queueSchedulerShutdownTimeoutInSeconds = chargeAsyncOperationsConfig.getQueueSchedulerShutdownTimeoutInSeconds();
    }

    @Override
    public void start() {
        int initialDelay = queueSchedulerThreadDelayInSeconds;
        chargeAsyncOperationMessageExecutorService.scheduleWithFixedDelay(
                this::chargeAsyncOperationMessageReceiver,
                initialDelay,
                queueSchedulerThreadDelayInSeconds,
                TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        LOGGER.info("Shutting down charge async operations service");
        chargeAsyncOperationMessageExecutorService.shutdown();
        try {
            if (chargeAsyncOperationMessageExecutorService.awaitTermination(queueSchedulerShutdownTimeoutInSeconds, TimeUnit.SECONDS)) {
                LOGGER.info("charge async operation service shut down cleanly");
            } else {
                LOGGER.error("Async charge operations still being processed after shutdown wait time will now be forcefully stopped");
                chargeAsyncOperationMessageExecutorService.shutdownNow();
                if (!chargeAsyncOperationMessageExecutorService.awaitTermination(12, TimeUnit.SECONDS)){
                    LOGGER.error("Charge async operations service could not be forced stopped");
                }
            }
        } catch (InterruptedException ex) {
            LOGGER.error("Failed to shutdown charge async operations service cleanly as the wait was interrupted.");
            chargeAsyncOperationMessageExecutorService.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    private void chargeAsyncOperationMessageReceiver() {
        try {
            chargeAsyncOperationsProcess.handleChargeAsyncOperationsMessage();
        } catch (Exception e) {
            LOGGER.error("Queue message chargeAsyncOperationMessageReceiver thread exception [message={}]", e.getMessage());
        }
    }
}
