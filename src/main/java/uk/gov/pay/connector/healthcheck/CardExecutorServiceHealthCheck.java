package uk.gov.pay.connector.healthcheck;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheck;
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.pay.connector.service.CardExecutorService;

import javax.inject.Inject;
import java.util.concurrent.ThreadPoolExecutor;

public class CardExecutorServiceHealthCheck extends HealthCheck {

    private ThreadPoolExecutor threadPoolExecutor;
    private static ObjectMapper mapper = new ObjectMapper();
    private MetricRegistry metricRegistry;

    @Inject
    public CardExecutorServiceHealthCheck(CardExecutorService cardExecutorService) {
        this.threadPoolExecutor = (ThreadPoolExecutor)cardExecutorService.getExecutor();
        this.metricRegistry = cardExecutorService.getMetricRegistry();
    }

    @Override
    protected Result check() throws Exception {
        captureMetrics();
        if (threadPoolExecutor.getQueue().size() <= 10) {
            return Result.healthy();
        }

        return Result.unhealthy("CardExecutorService-Unhealthy - Check metrics");
    }

    private void captureMetrics() {
        metricRegistry.histogram("card_executor.active-threads").update(threadPoolExecutor.getActiveCount());
        metricRegistry.histogram("card_executor.pool-size").update(threadPoolExecutor.getPoolSize());
        metricRegistry.histogram("card_executor.core-pool-size").update(threadPoolExecutor.getCorePoolSize());
        metricRegistry.histogram("card_executor.queue-size").update(threadPoolExecutor.getQueue() == null ? 0 : threadPoolExecutor.getQueue().size());
    }
}
