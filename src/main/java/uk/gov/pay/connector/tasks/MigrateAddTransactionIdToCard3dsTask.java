package uk.gov.pay.connector.tasks;

import com.google.common.collect.ImmutableMultimap;
import io.dropwizard.servlets.tasks.Task;

import javax.inject.Inject;
import java.io.PrintWriter;

public class MigrateAddTransactionIdToCard3dsTask extends Task {

    private static final String TASK_NAME = "migrate-add-transaction-id-to-card3ds";

    private AddTransactionIdToCard3dsWorker worker;

    @Inject
    public MigrateAddTransactionIdToCard3dsTask(AddTransactionIdToCard3dsWorker worker) {
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
