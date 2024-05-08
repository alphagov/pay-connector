package uk.gov.pay.connector.queue.managed;

import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.payout.PayoutReconcileProcess;

import javax.inject.Inject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PayoutReconcileMessageReceiver implements Managed {

    private static final String PAYOUT_RECONCILE_MESSAGE_RECEIVER_THREAD_NAME = "sqs-message-payoutReconcileMessageReceiver";

    private static final Logger LOGGER = LoggerFactory.getLogger(PayoutReconcileMessageReceiver.class);

    private final int queueSchedulerThreadDelayInSeconds;
    private final PayoutReconcileProcess payoutReconcileProcess;
    private final boolean payoutReconcileQueueEnabled;
    private ScheduledExecutorService payoutReconcileMessageExecutorService;

    @Inject
    public PayoutReconcileMessageReceiver(PayoutReconcileProcess payoutReconcileProcess, Environment environment,
                                          ConnectorConfiguration connectorConfiguration) {
        this.payoutReconcileProcess = payoutReconcileProcess;

        int queueScheduleNumberOfThreads = connectorConfiguration.getPayoutReconcileProcessConfig()
                .getQueueSchedulerNumberOfThreads();

        payoutReconcileMessageExecutorService = environment
                .lifecycle()
                .scheduledExecutorService(PAYOUT_RECONCILE_MESSAGE_RECEIVER_THREAD_NAME)
                .threads(queueScheduleNumberOfThreads)
                .build();

        queueSchedulerThreadDelayInSeconds = connectorConfiguration.getPayoutReconcileProcessConfig().getQueueSchedulerThreadDelayInSeconds();
        payoutReconcileQueueEnabled = connectorConfiguration.getPayoutReconcileProcessConfig().getPayoutReconcileQueueEnabled();
    }

    @Override
    public void start() {
        if (payoutReconcileQueueEnabled) {
            int initialDelay = queueSchedulerThreadDelayInSeconds;
            payoutReconcileMessageExecutorService.scheduleWithFixedDelay(
                    this::processPayouts,
                    initialDelay,
                    queueSchedulerThreadDelayInSeconds,
                    TimeUnit.SECONDS);
        }
    }

    @Override
    public void stop() {
        LOGGER.info("Shutting down payout reconciliation service");
        payoutReconcileMessageExecutorService.shutdownNow();
        LOGGER.info("Payout reconciliation service shut down");
    }

    private void processPayouts() {
        try {
            payoutReconcileProcess.processPayouts();
        } catch (Exception e) {
            LOGGER.warn("Queue message payoutReconcileMessageReceiver thread exception [class={} message={}]", e.getClass(), e.getMessage());
        }
    }
}
