package uk.gov.pay.connector.healthcheck;

import com.codahale.metrics.health.HealthCheck;
import com.google.inject.Provider;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.service.CardExecutorService;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.concurrent.ThreadPoolExecutor;

public class CardExecutorServiceHealthCheck extends HealthCheck {

    private ThreadPoolExecutor threadPoolExecutor;

    @Inject
    public CardExecutorServiceHealthCheck(CardExecutorService cardExecutorService) {
        this.threadPoolExecutor = (ThreadPoolExecutor)cardExecutorService.getExecutor();
    }

    @Override
    protected Result check() throws Exception {
        if (threadPoolExecutor.getActiveCount() < threadPoolExecutor.getMaximumPoolSize()) {
            return Result.healthy();
        }
        return Result.unhealthy("Card Executor thread pool exhausted");
    }
}