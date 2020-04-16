package uk.gov.pay.connector.queue.managed;

import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.paymentprocessor.service.CardCaptureProcess;

import javax.inject.Inject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CaptureMessageReceiver implements Managed {

    private static final String SQS_MESSAGE_RECEIVER_THREAD_NAME = "sqs-message-chargeCaptureMessageReceiver";

    private static final Logger LOGGER = LoggerFactory.getLogger(CaptureMessageReceiver.class);

    private final int queueSchedulerThreadDelayInSeconds;
    private final CardCaptureProcess cardCaptureProcess;
    private ScheduledExecutorService chargeCaptureMessageExecutorService;

    @Inject
    public CaptureMessageReceiver(CardCaptureProcess cardCaptureProcess, Environment environment,
                                  ConnectorConfiguration connectorConfiguration) {
        this.cardCaptureProcess = cardCaptureProcess;

        int queueScheduleNumberOfThreads = connectorConfiguration.getCaptureProcessConfig()
                .getQueueSchedulerNumberOfThreads();

        chargeCaptureMessageExecutorService = environment
                .lifecycle()
                .scheduledExecutorService(SQS_MESSAGE_RECEIVER_THREAD_NAME)
                .threads(queueScheduleNumberOfThreads)
                .build();

        queueSchedulerThreadDelayInSeconds = connectorConfiguration.getCaptureProcessConfig()
                .getQueueSchedulerThreadDelayInSeconds();
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
        chargeCaptureMessageExecutorService.shutdown();
    }

    private void chargeCaptureMessageReceiver() {
        try {
            cardCaptureProcess.handleCaptureMessages();
        } catch (Exception e) {
            LOGGER.error("Queue message chargeCaptureMessageReceiver thread exception [message={}]", e.getMessage());
        }
    }
}
