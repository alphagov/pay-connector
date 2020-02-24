package uk.gov.pay.connector.tasks;

import com.google.inject.Inject;
import io.dropwizard.servlets.tasks.Task;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.EventEmitterConfig;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static uk.gov.pay.connector.tasks.EventEmitterParamUtil.getOptionalLongParam;
import static uk.gov.pay.connector.tasks.EventEmitterParamUtil.getParameterValue;
import static uk.gov.pay.connector.tasks.RecordType.CHARGE;

public class HistoricalEventEmitterTask extends Task {
    private static final String TASK_NAME = "historical-event-emitter";
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private HistoricalEventEmitterWorker worker;
    private EventEmitterConfig eventEmitterConfig;
    private ExecutorService executor;

    public HistoricalEventEmitterTask() {
        super(TASK_NAME);
    }

    @Inject
    public HistoricalEventEmitterTask(Environment environment, ConnectorConfiguration connectorConfiguration,
                                      HistoricalEventEmitterWorker historicalEventEmitterWorker) {
        this();
        this.worker = historicalEventEmitterWorker;
        this.eventEmitterConfig = connectorConfiguration.getEventEmitterConfig();
        // Use of a synchronous work queue and a single thread pool ensures that only one job runs at a time
        // the queue has no capacity so attempts to put items into it will block if there is nothing trying 
        // to read from the queue. The thread pool size limit of 1 ensures only one item can run at a time.
        executor = environment
                .lifecycle()
                .executorService("HistoricalEventEmitterWorker-%d")
                .maxThreads(1)
                .workQueue(new SynchronousQueue<>())
                .build();
    }

    @Override
    public void execute(Map<String, List<String>> parameters, PrintWriter output) {
        Long startId = getOptionalLongParam(parameters, "start_id").orElse(0);
        final OptionalLong maybeMaxId = getOptionalLongParam(parameters, "max_id");
        final Long doNotRetryEmitUntilDuration = getDoNotRetryEmitUntilDuration(parameters);
        final RecordType recordType = getRecordType(parameters);

        logger.info("Execute called start_id={} max_id={} doNotRetryEmitUntilDuration={} - processing",
                startId, maybeMaxId, doNotRetryEmitUntilDuration);

        try {
            logger.info("Request accepted");

            if (CHARGE == recordType) {
                executor.execute(() -> worker.execute(startId, maybeMaxId, doNotRetryEmitUntilDuration));
            } else {
                executor.execute(() -> worker.executeForRefundsOnly(startId, maybeMaxId, doNotRetryEmitUntilDuration));
            }

            output.println("Accepted");
        } catch (java.util.concurrent.RejectedExecutionException e) {
            logger.info("Rejected request, worker already running");
            output.println("Rejected request, worker already running");
        }
    }

    private RecordType getRecordType(Map<String, List<String>> parameters) {
        String recordType = getParameterValue(parameters, "record_type");

        if (isEmpty(recordType)) {
            logger.info("Record type is not set available, defaulting to [{}]", CHARGE);
            return CHARGE;
        }

        return RecordType.fromString(recordType);
    }

    private Long getDoNotRetryEmitUntilDuration(Map<String, List<String>> parameters) {
        OptionalLong doNotRetryEmitUntil = getOptionalLongParam(parameters,
                "do_not_retry_emit_until");
        return doNotRetryEmitUntil.orElse(
                eventEmitterConfig.getDefaultDoNotRetryEmittingEventUntilDurationInSeconds());
    }
}
