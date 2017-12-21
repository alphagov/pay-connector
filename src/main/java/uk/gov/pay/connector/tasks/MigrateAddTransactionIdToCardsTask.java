package uk.gov.pay.connector.tasks;

import com.google.common.collect.ImmutableMultimap;
import io.dropwizard.servlets.tasks.Task;

import javax.inject.Inject;
import java.io.PrintWriter;

public class MigrateAddTransactionIdToCardsTask extends Task {

    private static final String TASK_NAME = "migrate-add-transaction-id-to-cards";

    private AddTransactionIdToCardsWorker worker;

    @Inject
    public MigrateAddTransactionIdToCardsTask(AddTransactionIdToCardsWorker worker) {
        super(TASK_NAME);
        this.worker = worker;
    }

    @Override
    public void execute(ImmutableMultimap<String, String> parameters, PrintWriter output) {
        String queryParam = "startId";
        Long startId = 1L;
        if (parameters.containsKey(queryParam)) {
            startId = Long.valueOf(parameters.get(queryParam).asList().get(0));
        }
        worker.execute(startId);
    }
}
