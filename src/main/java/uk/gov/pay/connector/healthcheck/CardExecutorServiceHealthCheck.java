package uk.gov.pay.connector.healthcheck;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.paymentprocessor.service.CardExecutorService;

import javax.inject.Inject;
import java.util.concurrent.ThreadPoolExecutor;

import static java.lang.String.format;

public class CardExecutorServiceHealthCheck extends HealthCheck {

    private final ThreadPoolExecutor threadPoolExecutor;

    private static final Logger logger = LoggerFactory.getLogger(CardExecutorServiceHealthCheck.class);


    @Inject
    public CardExecutorServiceHealthCheck(CardExecutorService cardExecutorService) {
        this.threadPoolExecutor = (ThreadPoolExecutor)cardExecutorService.getExecutor();
        initialiseMetrics(cardExecutorService.getMetricRegistry());
    }

    private void initialiseMetrics(MetricRegistry metricRegistry) {
        metricRegistry.<Gauge<Integer>>register("card-executor.active-threads", threadPoolExecutor::getActiveCount);
        metricRegistry.<Gauge<Integer>>register("card-executor.pool-size", threadPoolExecutor::getPoolSize);
        metricRegistry.<Gauge<Integer>>register("card-executor.core-pool-size", threadPoolExecutor::getCorePoolSize);
        metricRegistry.<Gauge<Integer>>register("card-executor.queue-size", () ->
                threadPoolExecutor.getQueue() == null ? 0 : threadPoolExecutor.getQueue().size());
    }

    @Override
    protected Result check() {
        if (threadPoolExecutor.getQueue().size() <= 10) {
            return Result.healthy();
        }
        
        // log some detail on the state of the threadPoolExecutor
        var queueSize = threadPoolExecutor.getQueue().size();
        var activelyExecutingThreads = threadPoolExecutor.getActiveCount();
        var corePoolSize = threadPoolExecutor.getCorePoolSize();
        
        logger.warn("Dumping thread pool executor stats: queue size {}, active threads {}, core pool size {}", queueSize, activelyExecutingThreads, corePoolSize);
        
        return Result.unhealthy("CardExecutorService-Unhealthy - Check metrics");
    }
}
