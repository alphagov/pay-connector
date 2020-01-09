package uk.gov.pay.connector.tasks;

import com.google.inject.Inject;
import io.dropwizard.servlets.tasks.Task;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.EventEmitterConfig;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;

import static uk.gov.pay.connector.tasks.EventEmitterParamUtil.getLongParam;
import static uk.gov.pay.connector.tasks.EventEmitterParamUtil.getParameterValue;

public class ParityCheckTask extends Task {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String TASK_NAME = "parity-checker";
    private static final String EXECUTOR_NAME_FORMAT = "ParityCheckWorker-%d";
    private EventEmitterConfig eventEmitterConfig;
    private ParityCheckWorker worker;
    private ExecutorService executor;

    public ParityCheckTask() {
        super(TASK_NAME);
    }

    @Inject
    public ParityCheckTask(ParityCheckWorker worker, Environment environment,
                           ConnectorConfiguration configuration) {
        this();
        this.worker = worker;

        // Use of a synchronous work queue and a single thread pool ensures that only one job runs at a time
        // the queue has no capacity so attempts to put items into it will block if there is nothing trying 
        // to read from the queue. The thread pool size limit of 1 ensures only one item can run at a time.
        executor = environment
                .lifecycle()
                .executorService(EXECUTOR_NAME_FORMAT)
                .maxThreads(1)
                .workQueue(new SynchronousQueue<>())
                .build();
        this.eventEmitterConfig = configuration.getEventEmitterConfig();
    }

    @Override
    public void execute(Map<String, List<String>> parameters, PrintWriter output) throws Exception {
        Long startId = getLongParam(parameters, "start_id").orElse(0L);
        final Optional<Long> maybeMaxId = getLongParam(parameters, "max_id");
        boolean doNotReprocessValidRecords = getFlagParam(parameters, "do_not_reprocess_valid_records").orElse(false);
        Optional<String> parityCheckStatus = getStringParam(parameters, "parity_check_status");
        final Long doNotRetryEmitUntilDuration = getDoNotRetryEmitUntilDuration(parameters);

        logger.info("Execute called start_id={} max_id={} - processing", startId, maybeMaxId);

        try {
            logger.info("Request accepted");
            executor.execute(() -> worker.execute(startId, maybeMaxId, doNotReprocessValidRecords,
                    parityCheckStatus, doNotRetryEmitUntilDuration));
            output.println("Accepted");
        } catch (java.util.concurrent.RejectedExecutionException e) {
            logger.info("Rejected request, worker already running");
            output.println("Rejected request, worker already running");
        }
    }

    private Optional<Boolean> getFlagParam(Map<String, List<String>> parameters, String paramName) {
        String value = getParameterValue(parameters, paramName);
        return Optional.ofNullable(StringUtils.isNotBlank(value) ?
                Boolean.valueOf(value) : null);
    }

    private Optional<String> getStringParam(Map<String, List<String>> parameters, String paramName) {
        return Optional.ofNullable(getParameterValue(parameters, paramName));
    }

    private Long getDoNotRetryEmitUntilDuration(Map<String, List<String>> parameters) {
        Optional<Long> doNotRetryEmitUntil = getLongParam(parameters,
                "do_not_retry_emit_until");
        return doNotRetryEmitUntil.orElse(
                eventEmitterConfig.getDefaultDoNotRetryEmittingEventUntilDurationInSeconds());
    }
}
