package uk.gov.pay.connector.tasks;

import com.google.common.collect.ImmutableMultimap;
import io.dropwizard.servlets.tasks.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;

public class HistoricalEventEmitterTask extends Task {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String TASK_NAME = "historical-event-emitter";

    public HistoricalEventEmitterTask() {
        super(TASK_NAME);
    }

    @Override
    public void execute(ImmutableMultimap<String, String> params, PrintWriter output) throws Exception {
        logger.info("Execute called");
    }
}
