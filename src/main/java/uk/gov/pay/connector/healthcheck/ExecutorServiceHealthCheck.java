package uk.gov.pay.connector.healthcheck;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheck;
import io.dropwizard.setup.Environment;

import javax.inject.Inject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

public class ExecutorServiceHealthCheck extends HealthCheck {

    private ThreadPoolExecutor threadPoolExecutor;

    @Inject
    public ExecutorServiceHealthCheck(ExecutorService executorService, Environment environment) {
        this.threadPoolExecutor = (ThreadPoolExecutor) executorService;
        initialiseMetrics(environment.metrics());
    }

    private void initialiseMetrics(MetricRegistry metricRegistry) {
        metricRegistry.<Gauge<Integer>>register("card-executor.active-threads", () -> threadPoolExecutor.getActiveCount());
        metricRegistry.<Gauge<Integer>>register("card-executor.pool-size", () -> threadPoolExecutor.getPoolSize());
        metricRegistry.<Gauge<Integer>>register("card-executor.core-pool-size", () -> threadPoolExecutor.getCorePoolSize());
        metricRegistry.<Gauge<Integer>>register("card-executor.queue-size", () ->
                threadPoolExecutor.getQueue() == null ? 0 : threadPoolExecutor.getQueue().size());
    }

    @Override
    protected Result check() {
        if (threadPoolExecutor.getQueue().size() <= 10) {
            return Result.healthy();
        }

        return Result.unhealthy("ConnectorExecutorService-Unhealthy - Check metrics");
    }
}
