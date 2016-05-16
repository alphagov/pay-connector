package uk.gov.pay.connector.healthcheck;

import com.codahale.metrics.health.HealthCheck;
import com.google.inject.Provider;
import uk.gov.pay.connector.app.ConnectorConfiguration;

import javax.inject.Inject;
import javax.persistence.EntityManager;

public class DatabaseHealthCheck extends HealthCheck {

    private Provider<EntityManager> entityManager;
    private String validationQuery;

    @Inject
    public DatabaseHealthCheck(Provider<EntityManager> entityManager, ConnectorConfiguration config) {
        this.entityManager = entityManager;
        this.validationQuery = config.getDataSourceFactory().getValidationQuery();
    }

    @Override
    protected Result check() throws Exception {
        //TODO: This may be exhausting the DB Connection Pool
//        entityManager.get().createNativeQuery(validationQuery).getSingleResult();
        return Result.healthy();
    }
}