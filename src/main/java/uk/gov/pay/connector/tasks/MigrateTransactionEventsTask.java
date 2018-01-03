package uk.gov.pay.connector.tasks;

import com.google.common.collect.ImmutableMultimap;
import io.dropwizard.servlets.tasks.Task;

import javax.inject.Inject;
import java.io.PrintWriter;

public class MigrateTransactionEventsTask extends Task {

    private static final String TASK_NAME = "migrate-charge-events-to-charge-transaction-events";

    private PaymentRequestWorker worker;

    private MigrateTransactionEventsTask(String name) {
        super(name);
    }

    @Inject
    public MigrateTransactionEventsTask(PaymentRequestWorker worker) {
        this(TASK_NAME);
        this.worker = worker;
    }

    @Override
    public void execute(ImmutableMultimap<String, String> parameters, PrintWriter output) {
        String queryParam = "startId";
        Long startId = 1L;
        if (!parameters.isEmpty() && parameters.containsKey(queryParam)) {
            startId = Long.valueOf(parameters.get(queryParam).asList().get(0));
        }
        worker.execute(startId);
    }
}
