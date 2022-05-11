package uk.gov.pay.connector.paymentprocessor.service;

import com.codahale.metrics.MetricRegistry;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.ExecutorServiceConfig;

import javax.ws.rs.WebApplicationException;
import java.util.Map;
import java.util.Optional;
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

/**
 * CardExecutorService executes tasks passed to it in a separate thread. The point of running tasks in a separate thread 
 * is that it can keep running after the originating thread has returned to the user. That is the purpose of the 
 * .get(config.getTimeoutInSeconds(), TimeUnit.SECONDS). If you look how that is used in the authorise service, it 
 * catches the timeout exception and returns to frontend as 'in progress'. Frontend then polls connector until the 
 * charge is authorised (by the CES thread), and continues on its merry way.
 */
public class CardExecutorService {

    private static final Logger logger = LoggerFactory.getLogger(CardExecutorService.class);
    private static final int QUEUE_WAIT_WARN_THRESHOLD_MILLIS = 1000;
    public static final int SHUTDOWN_AWAIT_TERMINATION_TIMEOUT_SECONDS = 10;
    private final MetricRegistry metricRegistry;

    private ExecutorServiceConfig config;
    private ExecutorService executor;

    public enum ExecutionStatus {
        COMPLETED,
        FAILED,
        IN_PROGRESS
    }

    @Inject
    public CardExecutorService(ConnectorConfiguration configuration, Environment environment) {
        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("CardExecutorService-%d")
                .build();
        this.metricRegistry = environment.metrics();
        this.config = configuration.getExecutorServiceConfig();
        int numberOfThreads = config.getThreadsPerCpu() * getRuntime().availableProcessors();
        this.executor = Executors.newFixedThreadPool(numberOfThreads, threadFactory);
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
                executor.awaitTermination(SHUTDOWN_AWAIT_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
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
    public <T> Pair<ExecutionStatus, T> execute(Supplier<T> callable, int timeoutInMilliseconds) {
        Callable<T> task = callable::get;
        Map<String, String> mdcContextMap = Optional.ofNullable(MDC.getCopyOfContextMap()).orElse(Map.of());
        final long startTime = System.currentTimeMillis();

        Future<T> futureObject = executor.submit(() -> {
            MDC.setContextMap(mdcContextMap);
            long totalWaitTime = System.currentTimeMillis() - startTime;
            logger.debug("Card operation task spent {} ms in queue", totalWaitTime);
            if (totalWaitTime > QUEUE_WAIT_WARN_THRESHOLD_MILLIS) {
                logger.warn("CardExecutor Service delay - queue_wait_time={}", totalWaitTime);
            }
            metricRegistry.histogram("card-executor.delay").update(totalWaitTime);
            try {
                return task.call();
            } finally {
                MDC.clear();
            }
        });

        try {
            return Pair.of(COMPLETED, futureObject.get(timeoutInMilliseconds, TimeUnit.MILLISECONDS));
        } catch (ExecutionException | InterruptedException exception) {
            if (exception.getCause() instanceof WebApplicationException) {
                throw (WebApplicationException) exception.getCause();
            } else if (exception.getCause() instanceof UnsupportedOperationException) {
                throw (UnsupportedOperationException) exception.getCause();
            }
            return Pair.of(FAILED, null);
        } catch (TimeoutException timeoutException) {
            return Pair.of(IN_PROGRESS, null);
        }
    }
}
