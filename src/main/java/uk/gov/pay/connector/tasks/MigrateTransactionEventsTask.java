package uk.gov.pay.connector.tasks;

import com.google.common.collect.ImmutableMultimap;
import io.dropwizard.servlets.tasks.Task;

import javax.inject.Inject;
import java.io.PrintWriter;

/**
 * A {@link Task} that will copy {@link uk.gov.pay.connector.model.domain.ChargeEventEntity}
 * to {@link uk.gov.pay.connector.model.domain.transaction.ChargeTransactionEventEntity}
 * <p>
 * The name of the class is important as this is the path on how it will be accessed from
 * Dropwizard
 */
public class MigrateTransactionEventsTask extends Task {

    private static final String TASK_NAME = "migrate-charge-events-to-charge-transaction-events";

    private PaymentRequestWorker worker;

    /**
     * Create a new task with the given name.
     *
     * @param name the task's name
     */
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
        worker.execute();
    }
}
