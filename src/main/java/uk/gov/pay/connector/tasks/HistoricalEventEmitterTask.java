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
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;

public class HistoricalEventEmitterTask extends Task {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String TASK_NAME = "historical-event-emitter";
    private HistoricalEventEmitterWorker worker;
    private EventEmitterConfig eventEmitterConfig;
    private Environment environment;
    private ExecutorService executor;

    public HistoricalEventEmitterTask() {
        super(TASK_NAME);
    }

    @Inject
    public HistoricalEventEmitterTask(Environment environment, ConnectorConfiguration connectorConfiguration,
                                      HistoricalEventEmitterWorker historicalEventEmitterWorker) {
        this();
        this.worker = historicalEventEmitterWorker;
        this.environment = environment;
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
    public void execute(ImmutableMultimap<String, String> parameters, PrintWriter output){
        Long startId = getParam(parameters, "start_id").orElse(0);
        final OptionalLong maybeMaxId = getParam(parameters, "max_id");
        final Long doNotRetryEmitUntilDuration = getDoNotRetryEmitUntilDuration(parameters);

        logger.info("Execute called start_id={} max_id={} doNotRetryEmitUntilDuration={} - processing",
                startId, maybeMaxId, doNotRetryEmitUntilDuration);
        
        try {
            logger.info("Request accepted");
            executor.execute(() -> worker.execute(startId, maybeMaxId, doNotRetryEmitUntilDuration));
            output.println("Accepted");
        }
        catch (java.util.concurrent.RejectedExecutionException e) {
            logger.info("Rejected request, worker already running");
            output.println("Rejected request, worker already running");
        }
    }

    private OptionalLong getParam(ImmutableMultimap<String, String> parameters, String paramName) {
        final ImmutableCollection<String> strings = parameters.get(paramName);
        
        if (strings.isEmpty()) {
            return OptionalLong.empty();
        } else {
            return OptionalLong.of(Long.valueOf(strings.asList().get(0)));
        }
    }

    private Long getDoNotRetryEmitUntilDuration(ImmutableMultimap<String, String> parameters) {
        OptionalLong doNotRetryEmitUntil = getParam(parameters,
                "do_not_retry_emit_until");
        return doNotRetryEmitUntil.orElse(
                eventEmitterConfig.getDefaultDoNotRetryEmittingEventUntilDurationInSeconds());
    }
}
