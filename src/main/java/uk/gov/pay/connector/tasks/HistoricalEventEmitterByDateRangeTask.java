package uk.gov.pay.connector.tasks;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMultimap;
import com.google.inject.Inject;
import io.dropwizard.servlets.tasks.Task;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;

public class HistoricalEventEmitterByDateRangeTask extends Task {
    private static final String TASK_NAME = "historical-event-emitter-by-date";
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private HistoricalEventEmitterWorker worker;
    private ExecutorService executor;

    public HistoricalEventEmitterByDateRangeTask() {
        super(TASK_NAME);
    }

    @Inject
    public HistoricalEventEmitterByDateRangeTask(HistoricalEventEmitterWorker worker, Environment environment) {
        this();
        this.worker = worker;

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
    public void execute(ImmutableMultimap<String, String> parameters, PrintWriter output) {
        Optional<ZonedDateTime> startDate = getParam(parameters, "start_date");
        Optional<ZonedDateTime> endDate = getParam(parameters, "end_date");

        logger.info("Execute called startDate={} endDate={} - processing", startDate, endDate);


        if (startDate.isEmpty() || endDate.isEmpty()) {
            logger.info("Rejected request, both start date and end date are mandatory");
            output.println("Rejected request, both start date and end date are mandatory");
        } else {
            try {
                logger.info("Request accepted");
                executor.execute(() -> worker.executeForDateRange(startDate.get(), endDate.get()));
                output.println("Accepted");
            } catch (java.util.concurrent.RejectedExecutionException e) {
                logger.info("Rejected request, worker already running");
                output.println("Rejected request, worker already running");
            }
        }
    }

    private Optional getParam(ImmutableMultimap<String, String> parameters, String paramName) {
        final ImmutableCollection<String> strings = parameters.get(paramName);

        if (strings.isEmpty()) {
            return Optional.empty();
        } else {
            try {
                return Optional.of(ZonedDateTime.parse(strings.asList().get(0)));
            } catch (DateTimeParseException exception) {
                return Optional.empty();
            }
        }
    }
}
