package uk.gov.pay.connector.tasks;

import com.google.common.collect.ImmutableMultimap;
import io.dropwizard.servlets.tasks.Task;

import javax.inject.Inject;
import java.io.PrintWriter;

public class MigrateEmailTask extends Task {

    private static final String TASK_NAME = "migrate-email";

    private MigrateEmailWorker worker;

    private MigrateEmailTask(String name) {
        super(name);
    }

    //Used by Guice injection
    @SuppressWarnings("unused")
    @Inject
    public MigrateEmailTask(MigrateEmailWorker worker) {
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
