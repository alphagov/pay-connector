package uk.gov.pay.connector.paymentprocessor.service;

import com.codahale.metrics.MetricRegistry;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.ExecutorServiceConfig;
import uk.gov.pay.connector.common.exception.CredentialsException;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.util.XrayUtils;

import javax.ws.rs.WebApplicationException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static java.lang.Runtime.getRuntime;
import static uk.gov.pay.connector.paymentprocessor.service.CardExecutorService.ExecutionStatus.COMPLETED;
import static uk.gov.pay.connector.paymentprocessor.service.CardExecutorService.ExecutionStatus.FAILED;
import static uk.gov.pay.connector.paymentprocessor.service.CardExecutorService.ExecutionStatus.IN_PROGRESS;

// this service runs the supplied function in a new Thread
public class CardExecutorService<T> {

    private static final Logger logger = LoggerFactory.getLogger(CardExecutorService.class);
    private static final int QUEUE_WAIT_WARN_THRESHOLD_MILLIS = 10000;
    private final MetricRegistry metricRegistry;

    private ExecutorServiceConfig config;
    private ExecutorService executor;
    private XrayUtils xrayUtils;

    public enum ExecutionStatus {
        COMPLETED,
        FAILED,
        IN_PROGRESS
    }

    @Inject
    public CardExecutorService(ConnectorConfiguration configuration, Environment environment, XrayUtils xrayUtils) {
        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("CardExecutorService-%d")
                .build();
        this.metricRegistry = environment.metrics();
        this.config = configuration.getExecutorServiceConfig();
        int numberOfThreads = config.getThreadsPerCpu() * getRuntime().availableProcessors();
        this.executor = Executors.newFixedThreadPool(numberOfThreads, threadFactory);
        this.xrayUtils = xrayUtils;
        addShutdownHook();
    }

    public MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }

    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            String className = CardExecutorService.class.getSimpleName();
            logger.info("Shutting down {}", className);
            executor.shutdown();
            logger.info("Awaiting for {} threads to terminate", className);
            try {
                executor.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.error("Error while waiting for {} threads to terminate", className);
            }
            executor.shutdownNow();
        }));
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    // accepts a supplier function and executed that in a separate Thread of its own.
    // returns a Pair of the execution status and the return type
    public Pair<ExecutionStatus, T> execute(Supplier<T> callable) {
        Callable<T> task = callable::get;
        final long startTime = System.currentTimeMillis();

        Future<T> futureObject = executor.submit(() -> {
            xrayUtils.beginSegment();
            long totalWaitTime = System.currentTimeMillis() - startTime;
            logger.debug("Card operation task spent {} ms in queue", totalWaitTime);
            if (totalWaitTime > QUEUE_WAIT_WARN_THRESHOLD_MILLIS) {
                logger.warn("CardExecutor Service delay - queue_wait_time={}", totalWaitTime);
            }
            metricRegistry.histogram("card-executor.delay").update(totalWaitTime);
            try {
                return task.call();
            } finally {
                xrayUtils.endSegment();
            }
        });

        try {
            return Pair.of(COMPLETED, futureObject.get(config.getTimeoutInSeconds(), TimeUnit.SECONDS));
        } catch (ExecutionException | InterruptedException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof WebApplicationException) {
                throw (WebApplicationException) cause;
            }
            if (cause instanceof CredentialsException) {
                throw (CredentialsException) cause;
            }
            if (cause instanceof GatewayError) {
                throw (GatewayError) cause;
            }
            return Pair.of(FAILED, null);
        } catch (TimeoutException timeoutException) {
            return Pair.of(IN_PROGRESS, null);
        }
    }
}
