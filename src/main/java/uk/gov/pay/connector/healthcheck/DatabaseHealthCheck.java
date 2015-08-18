package uk.gov.pay.connector.healthcheck;

import com.codahale.metrics.health.HealthCheck;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;

public class DatabaseHealthCheck extends HealthCheck {
    private final DBI database;
    private String validationQuery;

    public DatabaseHealthCheck(DBI database, String validationQuery) {
        this.database = database;
        this.validationQuery = validationQuery;
    }

    @Override
    protected Result check() throws Exception {
        return database.withHandle(h -> {
            h.createQuery(validationQuery).first();
            return Result.healthy();
        });
    }
}