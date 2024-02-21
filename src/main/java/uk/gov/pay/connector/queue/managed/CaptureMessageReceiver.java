package uk.gov.pay.connector.queue.managed;

import io.dropwizard.lifecycle.Managed;
import io.dropwizard.core.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.CaptureProcessConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.paymentprocessor.service.CardCaptureProcess;

import javax.inject.Inject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CaptureMessageReceiver implements Managed {

    private static final String SQS_MESSAGE_RECEIVER_THREAD_NAME = "sqs-message-chargeCaptureMessageReceiver";

    private static final Logger LOGGER = LoggerFactory.getLogger(CaptureMessageReceiver.class);

    private final int queueSchedulerThreadDelayInSeconds;
    private final int queueSchedulerShutdownTimeoutInSeconds;
    private final CardCaptureProcess cardCaptureProcess;
    private ScheduledExecutorService chargeCaptureMessageExecutorService;

    @Inject
    public CaptureMessageReceiver(CardCaptureProcess cardCaptureProcess, Environment environment,
                                  ConnectorConfiguration connectorConfiguration) {
        this.cardCaptureProcess = cardCaptureProcess;

        CaptureProcessConfig captureProcessConfig = connectorConfiguration.getCaptureProcessConfig();
        int queueScheduleNumberOfThreads = captureProcessConfig
                .getQueueSchedulerNumberOfThreads();

        chargeCaptureMessageExecutorService = environment
                .lifecycle()
                .scheduledExecutorService(SQS_MESSAGE_RECEIVER_THREAD_NAME)
                .threads(queueScheduleNumberOfThreads)
                .build();

        queueSchedulerThreadDelayInSeconds = captureProcessConfig.getQueueSchedulerThreadDelayInSeconds();
        queueSchedulerShutdownTimeoutInSeconds = captureProcessConfig.getQueueSchedulerShutdownTimeoutInSeconds();
    }

    @Override
    public void start() {
        int initialDelay = queueSchedulerThreadDelayInSeconds;
        chargeCaptureMessageExecutorService.scheduleWithFixedDelay(
                this::chargeCaptureMessageReceiver,
                initialDelay,
                queueSchedulerThreadDelayInSeconds,
                TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        LOGGER.info("Shutting down card capture service");
        chargeCaptureMessageExecutorService.shutdown();
        try {
            // Wait for existing charges to finish being captured
            if (chargeCaptureMessageExecutorService.awaitTermination(queueSchedulerShutdownTimeoutInSeconds, TimeUnit.SECONDS)) {
                LOGGER.info("card capture service shut down cleanly");
            } else {
                // If the existing charges being captured didn't terminate within the allowed time then force them to.
                LOGGER.error("Charges still being captured after shutdown wait time will now be forcefully stopped");
                chargeCaptureMessageExecutorService.shutdownNow();
                if (!chargeCaptureMessageExecutorService.awaitTermination(12, TimeUnit.SECONDS)){
                    LOGGER.error("Charge capture service could not be forced stopped");
                }
            }
        } catch (InterruptedException ex) {
            LOGGER.error("Failed to shutdown charge capture service cleanly as the wait was interrupted.");
            chargeCaptureMessageExecutorService.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
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
