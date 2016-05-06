package uk.gov.pay.connector.healthcheck;

import com.codahale.metrics.health.HealthCheck;
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.pay.connector.service.CardExecutorService;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

public class CardExecutorServiceHealthCheck extends HealthCheck {

    private ThreadPoolExecutor threadPoolExecutor;
    private static ObjectMapper mapper = new ObjectMapper();

    @Inject
    public CardExecutorServiceHealthCheck(CardExecutorService cardExecutorService) {
        this.threadPoolExecutor = (ThreadPoolExecutor)cardExecutorService.getExecutor();
    }

    @Override
    protected Result check() throws Exception {
        if (threadPoolExecutor.getQueue().size() <= 10) {
            return Result.healthy();
        }

        return Result.unhealthy(mapper.writeValueAsString(getStats()));
    }

    private Map<String, Integer> getStats() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("active-thread-count", threadPoolExecutor.getActiveCount());
        stats.put("pool-size", threadPoolExecutor.getPoolSize());
        stats.put("core-pool-size", threadPoolExecutor.getCorePoolSize());
        stats.put("queue-size", threadPoolExecutor.getQueue() == null ? 0 : threadPoolExecutor.getQueue().size());
        return stats;
    }
}