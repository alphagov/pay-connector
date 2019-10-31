package uk.gov.pay.connector.tasks;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMultimap;
import com.google.inject.Inject;
import io.dropwizard.servlets.tasks.Task;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.dao.EmittedEventDao;
import uk.gov.pay.connector.queue.StateTransitionService;
import uk.gov.pay.connector.refund.dao.RefundDao;

import java.io.PrintWriter;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;

public class HistoricalEventEmitterTask extends Task {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String TASK_NAME = "historical-event-emitter";
    private HistoricalEventEmitterWorker worker;
    private Environment environment;
    private ExecutorService executor;

    public HistoricalEventEmitterTask() {
        super(TASK_NAME);
    }

    @Inject
    public HistoricalEventEmitterTask(Environment environment, ChargeDao chargeDao, RefundDao refundDao,
                                      ChargeEventDao chargeEventDao, EmittedEventDao emittedEventDao,
                                      EventService eventService, StateTransitionService stateTransitionService) {
        this();

        HistoricalEventEmitter historicalEventEmitter = new HistoricalEventEmitter(emittedEventDao, refundDao,
                true, eventService, stateTransitionService);
        this.worker = new HistoricalEventEmitterWorker(chargeDao, refundDao, chargeEventDao, historicalEventEmitter);
        this.environment = environment;

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
    public void execute(ImmutableMultimap<String, String> parameters, PrintWriter output) throws Exception {
        Long startId = getLongParam(parameters, "start_id").orElse(0);
        final OptionalLong maybeMaxId = getLongParam(parameters, "max_id");
        Boolean patchBackfill = getBoolParam(parameters, "force_patch").orElse(false);

        logger.info("Execute called start_id={} max_id={} - processing", startId, maybeMaxId);
        
        try {
            logger.info("Request accepted");
            if (patchBackfill) {
                executor.execute(() -> worker.executePatchCreatedAndDetailsEntered(startId, maybeMaxId));
            } else {
                executor.execute(() -> worker.execute(startId, maybeMaxId));
            }
            output.println("Accepted");
        }
        catch (java.util.concurrent.RejectedExecutionException e) {
            logger.info("Rejected request, worker already running");
            output.println("Rejected request, worker already running");
        }
    }

    private OptionalLong getLongParam(ImmutableMultimap<String, String> parameters, String paramName) {
        final ImmutableCollection<String> strings = parameters.get(paramName);
        
        if (strings.isEmpty()) {
            return OptionalLong.empty();
        } else {
            return OptionalLong.of(Long.valueOf(strings.asList().get(0)));
        }
    }

    private Optional<Boolean> getBoolParam(ImmutableMultimap<String, String> parameters, String paramName) {
        final ImmutableCollection<String> strings = parameters.get(paramName);

        if (strings.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(strings.asList().get(0).equals("true"));
        }
    }
}
