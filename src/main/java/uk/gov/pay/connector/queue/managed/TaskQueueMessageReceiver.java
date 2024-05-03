package uk.gov.pay.connector.queue.managed;

import io.dropwizard.lifecycle.Managed;
import io.dropwizard.core.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.TaskQueueConfig;
import uk.gov.pay.connector.queue.tasks.TaskQueueMessageHandler;

import javax.inject.Inject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TaskQueueMessageReceiver implements Managed {

    private static final String TASK_QUEUE_MESSAGE_RECEIVER_THREAD_NAME = "sqs-message-tasksQueueMessageReceiver";

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskQueueMessageReceiver.class);

    private final int queueSchedulerThreadDelayInSeconds;
    private final int queueSchedulerShutdownTimeoutInSeconds;
    private final TaskQueueMessageHandler taskQueueMessageHandler;
    private boolean taskQueueEnabled;
    private ScheduledExecutorService tasksQueueScheduledExecutorService;

    @Inject
    public TaskQueueMessageReceiver(TaskQueueMessageHandler taskQueueMessageHandler, Environment environment,
                                    ConnectorConfiguration connectorConfiguration) {
        this.taskQueueMessageHandler = taskQueueMessageHandler;

        int queueScheduleNumberOfThreads = 1;

        tasksQueueScheduledExecutorService = environment
                .lifecycle()
                .scheduledExecutorService(TASK_QUEUE_MESSAGE_RECEIVER_THREAD_NAME)
                .threads(queueScheduleNumberOfThreads)
                .build();

        TaskQueueConfig taskQueueConfig = connectorConfiguration.getTaskQueueConfig();
        queueSchedulerThreadDelayInSeconds = taskQueueConfig.getQueueSchedulerThreadDelayInSeconds();
        queueSchedulerShutdownTimeoutInSeconds = taskQueueConfig.getQueueSchedulerShutdownTimeoutInSeconds();
        taskQueueEnabled = taskQueueConfig.getTaskQueueEnabled();
    }

    @Override
    public void start() {
        if (taskQueueEnabled) {
            int initialDelay = queueSchedulerThreadDelayInSeconds;
            tasksQueueScheduledExecutorService.scheduleWithFixedDelay(
                    this::processMessage,
                    initialDelay,
                    queueSchedulerThreadDelayInSeconds,
                    TimeUnit.SECONDS);
        }
    }

    @Override
    public void stop() {
        LOGGER.info("Shutting down task queue message receiver");
        tasksQueueScheduledExecutorService.shutdown();
        try {
            if (tasksQueueScheduledExecutorService.awaitTermination(queueSchedulerShutdownTimeoutInSeconds, TimeUnit.SECONDS)) {
                LOGGER.info("Task queue message receiver shut down cleanly");
            } else {
                LOGGER.error("Task queue still processing messages after shutdown wait time will now be forcefully stopped");
                tasksQueueScheduledExecutorService.shutdownNow();
                if (!tasksQueueScheduledExecutorService.awaitTermination(12, TimeUnit.SECONDS)) {
                    LOGGER.error("Task queue receiver could not be forced stopped");
                }
            }
        } catch (InterruptedException ex) {
            LOGGER.error("Failed to shutdown task queue message receiver cleanly as the wait was interrupted.");
            tasksQueueScheduledExecutorService.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    private void processMessage() {
        try {
            taskQueueMessageHandler.processMessages();
        } catch (Exception e) {
            LOGGER.error("Exception processing message from task queue [error message={}]", e.getMessage());
        }
    }
}
