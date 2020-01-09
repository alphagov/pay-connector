package uk.gov.pay.connector.tasks;

import com.google.inject.Inject;
import io.dropwizard.servlets.tasks.Task;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.EventEmitterConfig;

import java.io.PrintWriter;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;

import static uk.gov.pay.connector.tasks.EventEmitterParamUtil.getDateParam;
import static uk.gov.pay.connector.tasks.EventEmitterParamUtil.getOptionalLongParam;

public class HistoricalEventEmitterByDateRangeTask extends Task {
    private static final String TASK_NAME = "historical-event-emitter-by-date";
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private HistoricalEventEmitterWorker worker;
    private EventEmitterConfig eventEmitterConfig;
    private ExecutorService executor;

    public HistoricalEventEmitterByDateRangeTask() {
        super(TASK_NAME);
    }

    @Inject
    public HistoricalEventEmitterByDateRangeTask(HistoricalEventEmitterWorker worker, Environment environment,
                                                 ConnectorConfiguration connectorConfiguration) {
        this();
        this.worker = worker;
        this.eventEmitterConfig = connectorConfiguration.getEventEmitterConfig();

        // Use of a synchronous work queue and a single thread pool ensures that only one job runs at a time
        // the queue has no capacity so attempts to put items into it will block if there is nothing trying 
        // to read from the queue. The thread pool size limit of 1 ensures only one item can run at a time.
        executor = environment
                .lifecycle()
                .executorService("HistoricalEventEmitterByDateRange-%d")
                .maxThreads(1)
                .workQueue(new SynchronousQueue<>())
                .build();
    }
    @Override
    public void execute(Map<String, List<String>> parameters, PrintWriter output) throws Exception {
        try {
            Optional<ZonedDateTime> startDate = getDateParam(parameters, "start_date");
            Optional<ZonedDateTime> endDate = getDateParam(parameters, "end_date");
            final Long doNotRetryEmitUntilDuration = getDoNotRetryEmitUntilDuration(parameters);

            logger.info("Execute called startDate={} endDate={}, doNotRetryEmitUntilDuration={} - processing",
                    startDate, endDate, doNotRetryEmitUntilDuration);

            if (startDate.isEmpty() || endDate.isEmpty()) {
                logger.info("Rejected request, both start date and end date are mandatory");
                output.println("Rejected request, both start date and end date are mandatory");
            } else {

                logger.info("Request accepted");
                executor.execute(() -> worker.executeForDateRange(startDate.get(), endDate.get(),
                        doNotRetryEmitUntilDuration));
                output.println("Accepted");
            }
        } catch (java.util.concurrent.RejectedExecutionException | NumberFormatException e) {
            logger.info("Rejected request, worker already running");
            output.println("Rejected request, worker already running");
        }
    }

    private Long getDoNotRetryEmitUntilDuration(Map<String, List<String>> parameters) {
        OptionalLong doNotRetryEmitUntil = getOptionalLongParam(parameters,
                "do_not_retry_emit_until");
        return doNotRetryEmitUntil.orElse(
                eventEmitterConfig.getDefaultDoNotRetryEmittingEventUntilDurationInSeconds());
    }
}
