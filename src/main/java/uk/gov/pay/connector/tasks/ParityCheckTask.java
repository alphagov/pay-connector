package uk.gov.pay.connector.tasks;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMultimap;
import com.google.inject.Inject;
import io.dropwizard.servlets.tasks.Task;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.EventEmitterConfig;

import java.io.PrintWriter;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.function.Function;

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
    public void execute(ImmutableMultimap<String, String> parameters, PrintWriter output) throws Exception {
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

    private Optional getParam(ImmutableMultimap<String, String> parameters, String paramName,
                              Function<String, Object> convertFunction) {
        final ImmutableCollection<String> strings = parameters.get(paramName);

        if (strings.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(convertFunction.apply(strings.asList().get(0)));
        }
    }

    private Optional<Long> getLongParam(ImmutableMultimap<String, String> parameters, String paramName) {
        return getParam(parameters, paramName, v -> Long.valueOf(v));
    }

    private Optional<Boolean> getFlagParam(ImmutableMultimap<String, String> parameters, String paramName) {
        return getParam(parameters, paramName, v -> Boolean.valueOf(v));
    }

    private Optional<String> getStringParam(ImmutableMultimap<String, String> parameters, String paramName) {
        return getParam(parameters, paramName, v -> v);
    }

    private Long getDoNotRetryEmitUntilDuration(ImmutableMultimap<String, String> parameters) {
        Optional<Long> doNotRetryEmitUntil = getLongParam(parameters,
                "do_not_retry_emit_until");
        return doNotRetryEmitUntil.orElse(
                eventEmitterConfig.getDefaultDoNotRetryEmittingEventUntilDurationInSeconds());
    }
}
