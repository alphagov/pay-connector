package uk.gov.pay.connector.healthcheck;

import com.codahale.metrics.health.HealthCheck;
import com.google.inject.Provider;

import javax.inject.Inject;
import javax.persistence.EntityManager;

public class DatabaseHealthCheck extends HealthCheck {

    private Provider<EntityManager> entityManager;
    private String validationQuery;

    @Inject
    public DatabaseHealthCheck(Provider<EntityManager> entityManager, String validationQuery) {
        this.entityManager = entityManager;
        this.validationQuery = validationQuery;
    }

    @Override
    protected Result check() throws Exception {
        entityManager.get().createNativeQuery(validationQuery).getSingleResult();
        return Result.healthy();
    }
}